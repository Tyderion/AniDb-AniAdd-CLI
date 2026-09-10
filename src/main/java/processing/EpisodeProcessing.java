package processing;

import aniAdd.misc.ICallBack;
import aniAdd.misc.MultiKeyDict;
import cache.IAniDBFileRepository;
import cache.IFileHashMappingRepository;
import config.blocks.*;
import fileprocessor.FileProcessor;
import kodi.KodiMetadataGenerator;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import processing.FileInfo.FileAction;
import processing.tagsystem.TagSystemTags;
import udpapi.UdpApi;
import udpapi.command.FileCommand;
import udpapi.command.LogoutCommand;
import udpapi.command.MylistAddCommand;
import udpapi.command.MylistCommand;
import udpapi.query.Query;
import udpapi.reply.ReplyStatus;
import transcode.CodecNames;
import transcode.MediaInfo;
import transcode.MediaProber;
import transcode.Transcoder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Slf4j
public class EpisodeProcessing implements FileProcessor.Processor {

    private final KodiConfig kodiConfig;
    private final UdpApi api;
    private final FileConfig fileConfig;
    private final AniDbConfig aniDbConfig;
    private final DoOnFileSystem fileSystem;
    private final KodiMetadataGenerator kodiMetadataGenerator;
    private final FileRenamer fileRenamer;
    private final IAniDBFileRepository fileRepository;
    private final IFileHandler fileHandler;
    private final IFileHashMappingRepository hashMappingRepository;
    private final Transcoder transcoder;
    private final MediaProber mediaProber;
    private final List<ICallBack<ProcessingEvent>> eventHandlers = new ArrayList<>();
    private final List<ICallBack<List<FileInfo>>> scanRunFinishedHandlers = new ArrayList<>();
    /** Files of each directory scan still in progress. Holds the FileInfos themselves, which survive the logout clearing {@link #files}. */
    private final List<List<FileInfo>> openScanRuns = new ArrayList<>();

    private int lastFileId = 0;
    private boolean shouldShutdown;


    private enum KeyType {
        Id, Path
    }

    private final MultiKeyDict<KeyType, Object, FileInfo> files = new MultiKeyDict<>(KeyType.class,
            (type, fileInfo) -> type == KeyType.Id ? fileInfo.getId() : (type == KeyType.Path ? fileInfo.getFile().getAbsolutePath() : null));

    public EpisodeProcessing(
            FileConfig fileConfig,
            TagsConfig tagsConfig,
            AniDbConfig aniDbConfig,
            KodiConfig kodiConfig,
            UdpApi udpApi,
            KodiMetadataGenerator kodiMetadataGenerator,
            DoOnFileSystem fileSystem,
            IFileHandler fileHandler,
            IAniDBFileRepository fileRepository,
            IFileHashMappingRepository hashMappingRepository,
            Transcoder transcoder,
            MediaProber mediaProber) {
        this.fileConfig = fileConfig;
        this.kodiConfig = kodiConfig;
        this.api = udpApi;
        this.aniDbConfig = aniDbConfig;
        this.fileHandler = fileHandler;
        this.fileRenamer = new FileRenamer(fileHandler, tagsConfig);
        this.kodiMetadataGenerator = kodiMetadataGenerator;
        this.fileRepository = fileRepository;
        this.hashMappingRepository = hashMappingRepository;
        this.transcoder = transcoder;
        this.mediaProber = mediaProber;
        this.fileSystem = fileSystem;

        api.registerCallback(LogoutCommand.class, cmd -> {
            // Remove files after we automatically log out
            if (cmd.getCommand().isAutomatic()) {
                log.info("Logged out, clearing cached files");
                files.clear();
            }
        });

        api.registerCallback(FileCommand.class, this::onAniDbFileReply);
        api.registerCallback(MylistAddCommand.class, this::onAniDbMyListAddReply);
        api.registerCallback(MylistCommand.class, this::onAniDbMyListReply);
    }

    public void addListener(ICallBack<ProcessingEvent> handler) {
        eventHandlers.add(handler);
    }

    /**
     * Called once per directory scan, when every file that scan added has finished, with those files. Files added
     * individually (e.g. to mark them watched from kodi) belong to no scan and never reach this listener.
     * Fires before {@link ProcessingEvent#Done}.
     */
    public void addScanRunFinishedListener(ICallBack<List<FileInfo>> handler) {
        scanRunFinishedHandlers.add(handler);
    }

    private void sendEvent(ProcessingEvent event) {
        eventHandlers.forEach(handler -> handler.invoke(event));
    }

    private void nextStep(FileAction finishedStep, FileInfo fileInfo) {
        log.info(STR."Finished with \{finishedStep} for file \{fileInfo.getFile().getAbsolutePath()} with Id \{fileInfo.getId()}");
        val config = fileInfo.config();
        switch (finishedStep) {
            case Init -> {
                hashFile(fileInfo);
            }
            case HashFile -> {
                if (fileInfo.hasActionFailed(FileAction.HashFile)) {
                    log.error(STR."File \{fileInfo.getFile().getAbsolutePath()} with Id \{fileInfo.getId()} failed to hash. Skipping all other steps");
                    finalize(fileInfo);
                    return;
                }
                // Kodi metadata needs the AniDB file data too, so a metadata-only run (rename and
                // move both NONE) must still look the file up. Without this the chain stopped here
                // and the FileCmd branch below that handles exactly that case was unreachable.
                if (config.rename().mode() != RenameConfig.Mode.NONE ||
                        config.move().mode() != MoveConfig.Mode.NONE ||
                        kodiConfig.metadata().generate()) {
                    loadFileInfo(fileInfo);
                }
                if (config.mylist().add()) {
                    addToMyList(fileInfo);
                }
            }
            case FileCmd -> {
                if (fileInfo.hasActionFailed(FileAction.FileCmd)) {
                    // Error, file data not found, skip continuing with dependant steps
                    log.warn(STR."FileCommand for file \{fileInfo.getFile().getAbsolutePath()} with Id \{fileInfo.getId()} failed to get data. Skipping dependant steps");
                    finalize(fileInfo);
                    return;
                }
                // Convert only what AniDB knows: an unidentified file is left alone so it lands in the
                // unknown folder in its original format, and no encode is spent on it.
                if (startTranscode(fileInfo)) {
                    return;
                }
                describeLocalMedia(fileInfo);
                afterIdentification(fileInfo);
            }
            case Transcode -> {
                describeLocalMedia(fileInfo);
                afterIdentification(fileInfo);
            }
            case Rename -> {
                if (kodiConfig.metadata().generate()) {
                    if (kodiConfig.metadata().syncWatchedStateFromMylist()) {
                        loadWatchedState(fileInfo);
                    } else {
                        generateKodiMetadata(fileInfo);
                    }
                } else {
                    if (fileInfo.allDone()) {
                        finalize(fileInfo);
                    }
                }
            }
            case LoadWatchedState -> {
                if (kodiConfig.metadata().generate()) {
                    generateKodiMetadata(fileInfo);
                }
            }
            case MyListAddCmd, GenerateKodiMetadata -> {
                if (fileInfo.allDone()) {
                    finalize(fileInfo);
                }
            }

        }
    }

    /**
     * Rename, move, and Kodi metadata: everything that happens once the file is identified and, where
     * configured, converted.
     */
    private void afterIdentification(FileInfo fileInfo) {
        val config = fileInfo.config();
        if (config.rename().mode() != RenameConfig.Mode.NONE ||
                config.move().mode() != MoveConfig.Mode.NONE) {
            renameFile(fileInfo);
        } else if (kodiConfig.metadata().generate()) {
            if (kodiConfig.metadata().syncWatchedStateFromMylist()) {
                loadWatchedState(fileInfo);
            } else {
                generateKodiMetadata(fileInfo);
            }
        }
    }

    /**
     * @return true when an encode was queued and the pipeline should continue from the Transcode step.
     */
    private boolean startTranscode(FileInfo fileInfo) {
        if (fileInfo.isActionInProcess(FileAction.Transcode) || fileInfo.isActionDone(FileAction.Transcode)
                || fileInfo.hasActionFailed(FileAction.Transcode)) {
            return false;
        }
        val source = fileInfo.getWorkingFile().toPath();
        val sourceInfo = transcoder.matches(source);
        if (sourceInfo.isEmpty()) {
            return false;
        }
        fileInfo.startAction(FileAction.Transcode);
        transcoder.transcode(source, sourceInfo.get(), result -> onTranscodeDone(fileInfo, result));
        return true;
    }

    private void onTranscodeDone(FileInfo fileInfo, Optional<Transcoder.Result> result) {
        if (result.isEmpty()) {
            log.warn(STR."Keeping \{fileInfo.getWorkingFile().getName()} as it is, the transcode did not succeed");
            fileInfo.actionFailed(FileAction.Transcode);
            nextStep(FileAction.Transcode, fileInfo);
            return;
        }
        val identityEd2k = fileInfo.getEd2k();
        val identitySize = fileInfo.getIdentitySize();
        val originalName = fileInfo.getWorkingFile().getName();
        fileInfo.setTranscodedFile(result.get().file());
        // Re-hash so the new file can be recognised on any later run, and record what it came from
        // before anything else touches it.
        fileSystem.run(new FileParser(fileInfo.getWorkingFile(), fileInfo.getId(), (_, ed2k, crc32) -> {
            if (ed2k == null) {
                log.error(STR."Could not hash the converted file \{fileInfo.getWorkingFile()}. It stays on disk but will not be recognised on the next run.");
            } else {
                fileInfo.setLocalEd2k(ed2k);
                fileInfo.setLocalCrc32(crc32);
                fileInfo.setMapped(true);
                hashMappingRepository.save(ed2k, fileInfo.getWorkingFile().length(), identityEd2k, identitySize, originalName);
            }
            fileInfo.actionDone(FileAction.Transcode);
            nextStep(FileAction.Transcode, fileInfo);
        }, () -> shouldShutdown));
    }

    /**
     * For a locally produced file, AniDB's codec and CRC describe the release it was made from, not
     * what is on disk. Overriding both from the file itself keeps names honest, and it runs on every
     * later pass as well, because the mapping is what marks a file as locally produced.
     */
    private void describeLocalMedia(FileInfo fileInfo) {
        if (!fileInfo.isMapped()) {
            return;
        }
        if (fileInfo.getLocalCrc32() != null) {
            fileInfo.getData().put(TagSystemTags.FileCrc, fileInfo.getLocalCrc32());
        }
        val info = mediaProber.probe(fileInfo.getWorkingFile().toPath());
        info.map(MediaInfo::videoCodec)
                .map(CodecNames::toAniDbVideoCodec)
                .ifPresent(codec -> fileInfo.getData().put(TagSystemTags.FileVideoCodec, codec));
    }

    private void loadWatchedState(FileInfo fileInfo) {
        if (fileInfo.isActionInProcess(FileAction.LoadWatchedState) || fileInfo.isActionDone(FileAction.LoadWatchedState)) {
            return;
        }
        fileInfo.startAction(FileAction.LoadWatchedState);
        api.queueCommand(MylistCommand.Create(fileInfo.getAniDbFileId(), fileInfo.getId()));
    }

    private void generateKodiMetadata(FileInfo procFile) {
        if (procFile.isActionInProcess(FileAction.GenerateKodiMetadata) || procFile.isActionDone(FileAction.GenerateKodiMetadata)) {
            return;
        }
        procFile.startAction(FileAction.GenerateKodiMetadata);
        // Checked before writing: an NFO that already existed and is not overwritten gives Kodi nothing new to scan,
        // which is the common case for files that are only re-processed to mark them watched.
        val videoFile = procFile.getRenamedFile() != null ? procFile.getRenamedFile() : procFile.getFile().toPath();
        val overwrite = kodiConfig.metadata().overwrite();
        if (overwrite.episodes() || overwrite.movies() || !Files.exists(nfoFileFor(videoFile))) {
            procFile.setLibraryChanged(true);
        }
        kodiMetadataGenerator.generateMetadata(procFile, () -> {
            procFile.actionDone(FileAction.GenerateKodiMetadata);
            nextStep(FileAction.GenerateKodiMetadata, procFile);
        });
    }


    private static Path nfoFileFor(Path videoFile) {
        val name = videoFile.getFileName().toString();
        val dot = name.lastIndexOf('.');
        return videoFile.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + ".nfo");
    }

    private void loadFileInfo(FileInfo procFile) {
        if (procFile.isActionInProcess(FileAction.FileCmd) || procFile.isActionDone(FileAction.FileCmd)) {
            return;
        }
        procFile.startAction(FileAction.FileCmd);
        val cachedData = fileRepository.getAniDBFileData(procFile.getEd2k(), procFile.getIdentitySize());
        cachedData.ifPresentOrElse(fd -> {
            log.info(STR."Got cached data for file \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}");
            if (fd.getUpdatedAt() == null || fd.getUpdatedAt().plusDays(aniDbConfig.cache().ttlInDays()).isBefore(LocalDateTime.now())) {
                log.info(STR."Cached data for file \{procFile.getFile().getAbsolutePath()} with Hash \{procFile.getEd2k()} is outdated, loading new info");
                api.queueCommand(FileCommand.Create(procFile.getId(), procFile.getIdentitySize(), procFile.getEd2k()));
                return;
            }
            procFile.getData().putAll(fd.getTags());
            procFile.actionDone(FileAction.FileCmd);
            nextStep(FileAction.FileCmd, procFile);
        }, () -> {
            log.info(STR."Requesting data for file \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}");
            api.queueCommand(FileCommand.Create(procFile.getId(), procFile.getIdentitySize(), procFile.getEd2k()));
        });
    }

    private void addToMyList(FileInfo procFile) {
        if (procFile.isActionInProcess(FileAction.MyListAddCmd) || procFile.isActionDone(FileAction.MyListAddCmd)) {
            return;
        }
        procFile.startAction(FileAction.MyListAddCmd);
        api.queueCommand(MylistAddCommand.Create(
                procFile.getId(),
                procFile.getIdentitySize(),
                procFile.getEd2k(),
                procFile.config().mylist().storageType().value(),
                procFile.getWatched() != null && procFile.getWatched()));
    }

    private void hashFile(FileInfo fileInfo) {
        if (fileInfo.isActionInProcess(FileAction.HashFile) || fileInfo.isActionDone(FileAction.HashFile)) {
            // This should technically not happen
            return;
        }

        fileInfo.startAction(FileAction.HashFile);
        log.debug(STR."Processing file \{fileInfo.getFile().getAbsolutePath()} with Id \{fileInfo.getId()}");
        fileSystem.run(new FileParser(fileInfo.getWorkingFile(), fileInfo.getId(), this::onHashComputed, () -> shouldShutdown));
    }

    private void onHashComputed(Integer tag, String hash, String crc32) {
        if (!files.contains(KeyType.Id, tag)) {
            // This shouldn't actually happen
            return;
        }
        FileInfo procFile = files.get(KeyType.Id, tag);
        if (hash != null) {
            procFile.setLocalEd2k(hash);
            procFile.setLocalCrc32(crc32);
            resolveIdentity(procFile, hash);
            log.debug(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} has been hashed");
            procFile.actionDone(FileAction.HashFile);
            nextStep(FileAction.HashFile, procFile);
        } else {
            procFile.actionFailed(FileAction.HashFile);
            nextStep(FileAction.HashFile, procFile);
        }
    }

    /**
     * A file we produced ourselves has a hash and size AniDB has never seen. The mapping turns those
     * back into the original release's pair, so identification, MyList and the cache all behave as if
     * the file were still in its original format.
     */
    private void resolveIdentity(FileInfo procFile, String localEd2k) {
        val mapping = hashMappingRepository.get(localEd2k, procFile.getWorkingFile().length());
        if (mapping.isEmpty()) {
            procFile.setIdentity(localEd2k, procFile.getWorkingFile().length());
            return;
        }
        val original = mapping.get();
        procFile.setMapped(true);
        procFile.setIdentity(original.getOriginalEd2k(), original.getOriginalSize());
        log.info(STR."\{procFile.getWorkingFile().getName()} is a locally converted file, identifying it as \{original.getOriginalEd2k()} (\{original.getOriginalSize()} bytes)");
    }

    private void onAniDbFileReply(Query<FileCommand> query) {
        int fileId = query.getTag();
        if (!files.contains(KeyType.Id, fileId)) {
            return;
        }
        FileInfo procFile = files.get(KeyType.Id, fileId);
        val replyStatus = query.getReply().getReplyStatus();
        if (replyStatus == ReplyStatus.NO_SUCH_FILE
                || replyStatus == ReplyStatus.ILLEGAL_INPUT_OR_ACCESS_DENIED
                || replyStatus == ReplyStatus.MULTIPLE_FILES_FOUND) {
            procFile.actionFailed(FileAction.FileCmd);
            val errorMessage = switch (replyStatus) {
                case NO_SUCH_FILE -> "File not found";
                case ILLEGAL_INPUT_OR_ACCESS_DENIED -> "Illegal input or access denied";
                case MULTIPLE_FILES_FOUND -> "Multiple files found";
                default -> "Unknown error";
            };
            log.warn(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} returned error: \{replyStatus} - \{errorMessage}");
            if (replyStatus == ReplyStatus.NO_SUCH_FILE
                    && procFile.config().move().unknown().mode() == MoveConfig.HandlingConfig.Mode.MOVE
            ) {
                fileSystem.run(() -> {
                    File currentFile = procFile.getWorkingFile();
                    val unknownTargetPath = procFile.config().move().unknown().folder()
                            .resolve(currentFile.getParentFile().getName())
                            .resolve(currentFile.getName());
                    fileHandler.renameFile(currentFile.toPath(), unknownTargetPath);
                    nextStep(FileAction.FileCmd, procFile);
                });
            } else {
                nextStep(FileAction.FileCmd, procFile);
            }
        } else {
            FileCommand.AddReplyToDict(procFile.getData(), query.getReply(), procFile.getWatched());
            fileRepository.saveAniDBFileData(procFile.toAniDBFileData());
            log.debug(STR."Got DB Info for file \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}");
            procFile.actionDone(FileAction.FileCmd);
            nextStep(FileAction.FileCmd, procFile);
        }
    }

    private void onAniDbMyListAddReply(Query<MylistAddCommand> query) {
        val replyStatus = query.getReply().getReplyStatus();

        int fileId = query.getTag();
        if (!files.contains(KeyType.Id, fileId)) {
            // This shouldn't actually happen
            return;
        }
        FileInfo procFile = files.get(KeyType.Id, fileId);
        val configuration = procFile.config();

        if (replyStatus == ReplyStatus.MYLIST_ENTRY_ADDED
                || replyStatus == ReplyStatus.MYLIST_ENTRY_EDITED) {
            procFile.actionDone(FileAction.MyListAddCmd);
            log.info(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} successfully added/edited on MyList");
            procFile.actionDone(FileAction.MyListAddCmd);
            nextStep(FileAction.MyListAddCmd, procFile);
        } else if (replyStatus == ReplyStatus.FILE_ALREADY_IN_MYLIST) {
            if (configuration.mylist().overwrite()) {
                api.queueCommand(query.getCommand().WithEdit());
                log.debug(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} already added on MyList, retrying with edit");
            } else {
                log.debug(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} already added on MyList. Continuing with next step.");
                procFile.actionDone(FileAction.MyListAddCmd);
                nextStep(FileAction.MyListAddCmd, procFile);
            }
        } else {
            if (replyStatus == ReplyStatus.NO_SUCH_FILE
                    || replyStatus == ReplyStatus.NO_SUCH_ANIME
                    || replyStatus == ReplyStatus.NO_SUCH_GROUP) {
                log.warn(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} returned not found status");
            } else {
                log.warn(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} returned error \{replyStatus}");
            }
            procFile.actionFailed(FileAction.MyListAddCmd);
            nextStep(FileAction.MyListAddCmd, procFile);
        }
    }

    private void onAniDbMyListReply(Query<MylistCommand> query) {
        int fileId = query.getTag();
        if (!files.contains(KeyType.Id, fileId)) {
            // This shouldn't actually happen
            return;
        }
        FileInfo fileInfo = files.get(KeyType.Id, fileId);

        MylistCommand.setWatchedDate(query.getReply(), fileInfo);

        fileInfo.actionDone(FileAction.LoadWatchedState);
        nextStep(FileAction.LoadWatchedState, fileInfo);
    }

    private void renameFile(FileInfo procFile) {
        if (procFile.isActionInProcess(FileAction.Rename) || procFile.isActionDone(FileAction.Rename)) {
            return;
        }
        procFile.startAction(FileAction.Rename);
        fileSystem.run(() -> {
            if (fileRenamer.renameFile(procFile)) {
                if (procFile.getRenamedFile() != null) {
                    procFile.setLibraryChanged(true);
                }
                procFile.actionDone(FileAction.Rename);
            } else {
                procFile.actionFailed(FileAction.Rename);
            }
            fileRepository.saveAniDBFileData(procFile.toAniDBFileData());
            nextStep(FileAction.Rename, procFile);
        });
    }

    private void finalize(FileInfo procFile) {
        if (!procFile.allDone()) {
            log.warn("Tried to finalize file that still has actions in progress");
            return;
        }
        log.debug(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} done");
        procFile.setFinished(true);
        completeFinishedScanRuns();
        if (files.values().stream().allMatch(FileInfo::allDone)) {
            sendEvent(ProcessingEvent.Done);
        }
    }

    private void completeFinishedScanRuns() {
        final List<List<FileInfo>> finished = new ArrayList<>();
        synchronized (openScanRuns) {
            openScanRuns.removeIf(run -> run.stream().allMatch(FileInfo::isFinished) && finished.add(run));
        }
        finished.forEach(run -> scanRunFinishedHandlers.forEach(handler -> handler.invoke(run)));
    }

    @Override
    public void addScanRun(Collection<File> newFiles) {
        val run = register(newFiles, fileConfig);
        // Registered before any file starts, so even a file finishing instantly is counted against its run.
        synchronized (openScanRuns) {
            openScanRuns.add(run);
        }
        if (run.isEmpty()) {
            // Everything found was already processed earlier: the run is over before it began.
            completeFinishedScanRuns();
        }
        start(run);
    }

    @Override
    public void addFiles(Collection<File> newFiles, FileConfig configuration) {
        start(register(newFiles, configuration));
    }

    private List<FileInfo> register(Collection<File> newFiles, FileConfig configuration) {
        Boolean watched = configuration.mylist().watched() ? true : null;
        val added = new ArrayList<FileInfo>();
        for (File file : newFiles) {
            if (files.contains(KeyType.Path, file.getAbsolutePath())) {
                log.info(STR."File \{file.getAbsolutePath()} already in processing/processed");
                continue;
            }

            FileInfo fileInfo = new FileInfo(file, lastFileId, watched, configuration);
            files.put(fileInfo);
            lastFileId++;
            added.add(fileInfo);
        }
        log.debug(STR."File Count changed to \{files.size()}");
        return added;
    }

    private void start(List<FileInfo> added) {
        for (val fileInfo : added) {
            fileInfo.actionDone(FileAction.Init);
            nextStep(FileAction.Init, fileInfo);
        }
    }

    public void Terminate() {
        shouldShutdown = true;
    }

    public enum ProcessingEvent {
        Done
    }
}

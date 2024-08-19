package processing;

import aniAdd.misc.ICallBack;
import aniAdd.misc.MultiKeyDict;
import cache.IAniDBFileRepository;
import config.CliConfiguration;
import fileprocessor.FileProcessor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import processing.FileInfo.FileAction;
import processing.tagsystem.TagSystemTags;
import udpapi.UdpApi;
import udpapi.command.FileCommand;
import udpapi.command.LogoutCommand;
import udpapi.command.MylistAddCommand;
import udpapi.query.Query;
import udpapi.reply.ReplyStatus;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public class EpisodeProcessing implements FileProcessor.Processor {

    private final UdpApi api;
    private final CliConfiguration defaultConfiguration;
    private final DoOnFileSystem fileSystem;
    private final FileRenamer fileRenamer;
    private final IAniDBFileRepository fileRepository;
    private final IFileHandler fileHandler;
    private final List<ICallBack<ProcessingEvent>> eventHandlers = new ArrayList<>();

    private int lastFileId = 0;
    private boolean shouldShutdown;


    private enum KeyType {
        Id, Path
    }

    private final MultiKeyDict<KeyType, Object, FileInfo> files = new MultiKeyDict<>(KeyType.class,
            (type, fileInfo) -> type == KeyType.Id ? fileInfo.getId() : (type == KeyType.Path ? fileInfo.getFile().getAbsolutePath() : null));

    public EpisodeProcessing(CliConfiguration configuration,
                             UdpApi udpApi,
                             DoOnFileSystem fileSystem,
                             IFileHandler fileHandler,
                             IAniDBFileRepository fileRepository) {
        this.defaultConfiguration = configuration;
        this.api = udpApi;
        this.fileHandler = fileHandler;
        this.fileRenamer = new FileRenamer(fileHandler);
        this.fileRepository = fileRepository;
        this.fileSystem = fileSystem;

        api.registerCallback(LogoutCommand.class, cmd -> {
            // Remove files after we automatically log out
            if (cmd.getCommand().isAutomatic()) {
                log.info("Logged out, clearing cached files");
                files.clear();
            }
        });

        api.registerCallback(FileCommand.class, this::aniDBInfoReply);
        api.registerCallback(MylistAddCommand.class, this::aniDBMyListReply);
    }

    public void addListener(ICallBack<ProcessingEvent> handler) {
        eventHandlers.add(handler);
    }

    private void sendEvent(ProcessingEvent event) {
        eventHandlers.forEach(handler -> handler.invoke(event));
    }

    private void nextStep(FileAction finishedStep, FileInfo fileInfo) {
        log.info(STR."Finished with \{finishedStep} for file \{fileInfo.getFile().getAbsolutePath()} with Id \{fileInfo.getId()}");
        val configuration = fileInfo.getConfiguration();
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
                if (configuration.getRename().getMode() != CliConfiguration.RenameConfig.Mode.NONE ||
                        configuration.getMove().getMode() != CliConfiguration.MoveConfig.Mode.NONE) {
                    loadFileInfo(fileInfo);
                }
                if (configuration.getMylist().isAdd()) {
                    addToMyList(fileInfo);
                }
            }
            case FileCmd -> {
                if (fileInfo.hasActionFailed(FileAction.FileCmd)) {
                    // Error, file data not found, skip continuing with dependant steps
                    log.warn(STR."FileCommand for file \{fileInfo.getFile().getAbsolutePath()} with Id \{fileInfo.getId()} failed to get data. Skipping dependant steps");
                    return;
                }
                if (configuration.getRename().getMode() != CliConfiguration.RenameConfig.Mode.NONE ||
                        configuration.getMove().getMode() != CliConfiguration.MoveConfig.Mode.NONE) {
                    renameFile(fileInfo);
                }
            }
            case MyListAddCmd, Rename -> {
                if (fileInfo.allDone()) {
                    finalize(fileInfo);
                }
            }
        }
    }

    private void loadFileInfo(FileInfo procFile) {
        if (procFile.isActionInProcess(FileAction.FileCmd) || procFile.isActionDone(FileAction.FileCmd)) {
            return;
        }
        procFile.startAction(FileAction.FileCmd);
        val cachedData = fileRepository.getAniDBFileData(procFile.getEd2k(), procFile.getFileSize());
        cachedData.ifPresentOrElse(fd -> {
            log.info(STR."Got cached data for file \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}");
            if (fd.getUpdatedAt() == null || fd.getUpdatedAt().plusDays(procFile.getConfiguration().getAnidb().getCache().getTtlInDays()).isBefore(LocalDateTime.now())) {
                log.info(STR."Cached data for file \{procFile.getFile().getAbsolutePath()} with Hash \{procFile.getEd2k()} is outdated, loading new info");
                api.queueCommand(FileCommand.Create(procFile.getId(), procFile.getFileSize(), procFile.getEd2k()));
                return;
            }
            procFile.getData().putAll(fd.getTags());
            procFile.actionDone(FileAction.FileCmd);
            nextStep(FileAction.FileCmd, procFile);
        }, () -> {
            log.info(STR."Requesting data for file \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}");
            api.queueCommand(FileCommand.Create(procFile.getId(), procFile.getFileSize(), procFile.getEd2k()));
        });
    }

    private void addToMyList(FileInfo procFile) {
        if (procFile.isActionInProcess(FileAction.MyListAddCmd) || procFile.isActionDone(FileAction.MyListAddCmd)) {
            return;
        }
        procFile.startAction(FileAction.MyListAddCmd);
        api.queueCommand(MylistAddCommand.Create(
                procFile.getId(),
                procFile.getFile().length(),
                procFile.getEd2k(),
                procFile.getConfiguration().getMylist().getStorageType().getValue(),
                procFile.getWatched() != null && procFile.getWatched()));
    }

    private void hashFile(FileInfo fileInfo) {
        if (fileInfo.isActionInProcess(FileAction.HashFile) || fileInfo.isActionDone(FileAction.HashFile)) {
            // This should technically not happen
            return;
        }

        fileInfo.startAction(FileAction.HashFile);
        log.debug(STR."Processing file \{fileInfo.getFile().getAbsolutePath()} with Id \{fileInfo.getId()}");
        fileSystem.run(new FileParser(fileInfo.getFile(), fileInfo.getId(), this::onHashComputed, () -> shouldShutdown));
    }

    private void onHashComputed(Integer tag, String hash) {
        if (!files.contains(KeyType.Id, tag)) {
            // This shouldn't actually happen
            return;
        }
        FileInfo procFile = files.get(KeyType.Id, tag);
        if (hash != null) {
            procFile.getData().put(TagSystemTags.Ed2kHash, hash);
            log.debug(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} has been hashed");
            procFile.actionDone(FileAction.HashFile);
            nextStep(FileAction.HashFile, procFile);
        } else {
            procFile.actionFailed(FileAction.HashFile);
            nextStep(FileAction.HashFile, procFile);
        }
    }

    private void aniDBInfoReply(Query<FileCommand> query) {
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
                    && procFile.getConfiguration().getMove().getUnknown().getMode() == CliConfiguration.MoveConfig.HandlingConfig.Mode.MOVE
            ) {
                fileSystem.run(() -> {
                    File currentFile = procFile.getFile();
                    val unknownTargetPath = Paths.get(procFile.getConfiguration().getMove().getUnknown().getFolder(), currentFile.getParentFile().getName(), currentFile.getName());
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

    private void aniDBMyListReply(Query<MylistAddCommand> query) {
        val replyStatus = query.getReply().getReplyStatus();

        int fileId = query.getTag();
        if (!files.contains(KeyType.Id, fileId)) {
            // This shouldn't actually happen
            return;
        }
        FileInfo procFile = files.get(KeyType.Id, fileId);
        val configuration = procFile.getConfiguration();

        if (replyStatus == ReplyStatus.MYLIST_ENTRY_ADDED
                || replyStatus == ReplyStatus.MYLIST_ENTRY_EDITED) {
            procFile.actionDone(FileAction.MyListAddCmd);
            log.info(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} successfully added/edited on MyList");
            procFile.actionDone(FileAction.MyListAddCmd);
            nextStep(FileAction.MyListAddCmd, procFile);
        } else if (replyStatus == ReplyStatus.FILE_ALREADY_IN_MYLIST) {
            if (configuration.getMylist().isOverwrite()) {
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

    private void renameFile(FileInfo procFile) {
        if (procFile.isActionInProcess(FileAction.Rename) || procFile.isActionDone(FileAction.Rename)) {
            return;
        }
        procFile.startAction(FileAction.Rename);
        fileSystem.run(() -> {
            if (fileRenamer.renameFile(procFile)) {
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
        if (files.values().stream().allMatch(FileInfo::allDone)) {
            sendEvent(ProcessingEvent.Done);
        }
    }

    public void addFiles(Collection<File> newFiles) {
        addFiles(newFiles, defaultConfiguration);
    }

    public void addFiles(Collection<File> newFiles, CliConfiguration configuration) {
        Boolean watched = configuration.getMylist().isWatched() ? true : null;

        for (File file : newFiles) {
            if (files.contains(KeyType.Path, file.getAbsolutePath())) {
                log.info(STR."File \{file.getAbsolutePath()} already in processing/processed");
                continue;
            }

            FileInfo fileInfo = new FileInfo(file, lastFileId);
            fileInfo.setConfiguration(configuration);
            fileInfo.setWatched(watched);

            files.put(fileInfo);
            lastFileId++;
            fileInfo.actionDone(FileAction.Init);
            nextStep(FileAction.Init, fileInfo);
        }
        log.debug(STR."File Count changed to \{files.size()}");
    }

    public void Terminate() {
        shouldShutdown = true;
    }

    public enum ProcessingEvent {
        Done
    }
}

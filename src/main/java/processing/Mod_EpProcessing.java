package processing;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import aniAdd.config.AniConfiguration;
import aniAdd.misc.ICallBack;
import fileprocessor.FileProcessor;
import lombok.extern.java.Log;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import processing.FileInfo.FileAction;

import aniAdd.misc.MultiKeyDict;

import java.util.concurrent.ExecutorService;

import processing.tagsystem.TagSystem;
import processing.tagsystem.TagSystemResult;
import processing.tagsystem.TagSystemTags;
import udpapi2.UdpApi;
import udpapi2.command.FileCommand;
import udpapi2.command.LogoutCommand;
import udpapi2.command.MylistAddCommand;
import udpapi2.query.Query;
import udpapi2.reply.ReplyStatus;

@Log
public class Mod_EpProcessing implements FileProcessor.Processor {

    public static String[] supportedFiles = {"avi", "ac3", "mpg", "mpeg", "rm", "rmvb", "asf", "wmv", "mov", "ogm", "mp4", "mkv", "rar", "zip", "ace", "srt", "sub", "ssa", "smi", "idx", "ass", "txt", "swf", "flv"};
    private final UdpApi api;
    private final AniConfiguration configuration;
    private final ExecutorService executorService;
    private final IFileHandler fileRenamer;
    private final List<ICallBack<ProcessingEvent>> eventHandlers = new ArrayList<>();

    private boolean isProcessing;
    private int lastFileId = 0;
    private int filesBeingMoved;
    private boolean shouldShutdown;


    private enum KeyType {
        Id, Path
    }

    private final MultiKeyDict<KeyType, Object, FileInfo> files = new MultiKeyDict<>(KeyType.class,
            (type, fileInfo) -> type == KeyType.Id ? fileInfo.getId() : (type == KeyType.Path ? fileInfo.getFile().getAbsolutePath() : null));

    public Mod_EpProcessing(AniConfiguration configuration, UdpApi udpApi, ExecutorService executorService, IFileHandler fileRenamer) {
        this.configuration = configuration;
        this.api = udpApi;
        this.executorService = executorService;
        this.fileRenamer = fileRenamer;

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

    private void processEps() {
        for (FileInfo procFile : files.values()) {
            if (!procFile.isServed()) {
                procFile.setServed(true);
                log.fine(STR."Processing file \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}");

                while (filesBeingMoved > 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                    }
                }

                executorService.execute(new FileParser(procFile.getFile(), procFile.getId(), this::onHashComputed, () -> shouldShutdown));

                return;
            }
        }
        isProcessing = false;
        log.info("Initial Processing done");
    }

    private void onHashComputed(Integer tag, String hash) {
        FileInfo procFile = files.get(KeyType.Id, tag);


        if (procFile != null && hash != null) {
            procFile.getData().put(TagSystemTags.Ed2kHash, hash);
            procFile.actionDone(FileAction.Process);
            log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} has been hashed");

            boolean sendML = procFile.isActionTodo(FileAction.MyListCmd);
            boolean sendFile = procFile.isActionTodo(FileAction.FileCmd);

            if (sendFile) {
                api.queueCommand(FileCommand.Create(procFile.getId(), procFile.getFile().length(), procFile.getData().get(TagSystemTags.Ed2kHash)));
            }
            if (sendML) {
                api.queueCommand(MylistAddCommand.Create(
                        procFile.getId(),
                        procFile.getFile().length(),
                        procFile.getData().get(TagSystemTags.Ed2kHash),
                        procFile.getConfiguration().getSetStorageType().getValue(),
                        procFile.getWatched() != null && procFile.getWatched()));
            }

            log.fine(STR."Requested Data for file with Id \{procFile.getId()}: SendFile: \{sendFile}, SendML: \{sendML}");

        } else if (procFile != null) {
            procFile.actionFailed(FileAction.Process);
            log.warning(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} could not be hashed");
        }

        if (isProcessing) {
            processEps();
        }
    }

    private void aniDBInfoReply(Query<FileCommand> query) {
        int fileId = query.getTag();
        if (!files.contains(KeyType.Id, fileId)) {
            return; //File not found (Todo: throw error)
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
            log.warning(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} returned error: \{errorMessage}");
            if (replyStatus == ReplyStatus.NO_SUCH_FILE && configuration.isMoveUnknownFiles()) {
                File currentFile = procFile.getFile();
                val unknownTargetPath = Paths.get(configuration.getUnknownFolder(), currentFile.getParentFile().getName(), currentFile.getName());
                fileRenamer.renameFile(currentFile.toPath(), unknownTargetPath);
            }
        } else {
            procFile.actionDone(FileAction.FileCmd);
            query.getCommand().AddReplyToDict(procFile.getData(), query.getReply(), procFile.getWatched());
            log.fine(STR."Got DB Info for file \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}");
        }

        if (shouldRunFinalProcessing(procFile)) {
            finalProcessing(procFile);
        }
    }

    private void aniDBMyListReply(Query<MylistAddCommand> query) {
        //System.out.println("Got ML Reply");
        val replyStatus = query.getReply().getReplyStatus();

        int fileId = query.getTag();
        if (!files.contains(KeyType.Id, fileId)) {
            //System.out.println("MLCmd: Id not found");
            return; //File not found (Todo: throw error)
        }
        FileInfo procFile = files.get(KeyType.Id, fileId);
        val configuration = procFile.getConfiguration();

        if (replyStatus == ReplyStatus.MYLIST_ENTRY_ADDED
                || replyStatus == ReplyStatus.MYLIST_ENTRY_EDITED) {
            procFile.actionDone(FileAction.MyListCmd);
            log.info(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} successfully added/edited on MyList");

        } else if (replyStatus == ReplyStatus.FILE_ALREADY_IN_MYLIST) {
            if (configuration.isOverwriteMLEntries()) {
                api.queueCommand(query.getCommand().WithEdit());
                log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} already added on MyList, retrying with edit");
            } else {
                log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} already added on MyList");
            }
        } else {
            procFile.actionFailed(FileAction.MyListCmd);
            if (replyStatus == ReplyStatus.NO_SUCH_FILE
                    || replyStatus == ReplyStatus.NO_SUCH_ANIME
                    || replyStatus == ReplyStatus.NO_SUCH_GROUP) {
                log.warning(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} returned not found status");
            } else {
                log.warning(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} returned error \{replyStatus}");
            }
        }

        if (shouldRunFinalProcessing(procFile)) {
            finalProcessing(procFile);
        }
    }

    private boolean shouldRunFinalProcessing(FileInfo procFile) {
        return !procFile.isFinal() && !(procFile.isActionTodo(FileAction.FileCmd) || (procFile.isActionTodo(FileAction.MyListCmd)));
    }

    private void finalProcessing(FileInfo procFile) {
        procFile.setFinal(true);

        if (procFile.isActionTodo(FileAction.Rename) && procFile.isActionDone(FileAction.FileCmd)) {

            while (filesBeingMoved > 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                }
            }

            synchronized (this) {
                filesBeingMoved++;
            }

            try {
                if (renameFile(procFile)) {
                    procFile.actionDone(FileAction.Rename);
                } else {
                    procFile.actionFailed(FileAction.Rename);
                }
            } catch (Exception e) {
            }

            synchronized (this) {
                filesBeingMoved--;
            }
        }

        log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} done");
        if (procFile.getId() == lastFileId - 1) {
            sendEvent(ProcessingEvent.Done);
        }
    }

    private Optional<String> getTargetFileName(FileInfo procFile, TagSystemResult tagSystemResult) throws Exception {
        val configuration = procFile.getConfiguration();
        if (!configuration.isEnableFileRenaming()) {
            return Optional.of(procFile.getFile().getName());
        }
        if (configuration.isRenameTypeAniDBFileName()) {
            return Optional.of(procFile.getData().get(TagSystemTags.FileAnidbFilename));
        }
        var tsResult = tagSystemResult == null ? getPathFromTagSystem(procFile) : tagSystemResult;
        if (tsResult == null) {
            log.severe(STR."TagSystem script failed for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}. Check your tag system code.");
            return Optional.empty();
        }

        return Optional.of(tsResult.FileName());
    }


    private boolean renameFile(FileInfo procFile) {
        val configuration = procFile.getConfiguration();
        try {

            val targetFolder = getTargetFolder(procFile);
            val targetFileName = getTargetFileName(procFile, targetFolder.getRight());

            if (targetFileName.isEmpty()) {
                return false;
            }

            var filename = targetFileName.get();
            filename = filename.replaceAll("[\\\\:\"/*|<>?]", "");

            val extension = filename.substring(filename.lastIndexOf("."));
            val targetFolderPath = targetFolder.getLeft();

            if (targetFileName.get().length() + targetFolderPath.toString().length() > 240) {
                filename = filename.substring(0, 240 - targetFolderPath.toString().length() - extension.length()) + extension;
            }

            val targetFilePath = targetFolderPath.resolve(filename);

            if (Files.exists(targetFilePath)) {
                log.info(STR."Destination for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} already exists: \{targetFilePath.toString()}");
                if (configuration.isDeleteDuplicateFiles()) {
                    fileRenamer.deleteFile(procFile.getFile().toPath());
                } else if (configuration.isMoveDuplicateFiles()) {
                    fileRenamer.renameFile(procFile.getFile().toPath(), Paths.get(configuration.getDuplicatesFolder()).resolve(targetFilePath));
                }
                return false;
            }
            if (targetFilePath.equals(procFile.getFile().toPath().toAbsolutePath())) {
                log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} does not need renaming.");
                return true;
            }

            final String oldFilename = procFile.getFile().getName();
            if (fileRenamer.renameFile(procFile.getFile().toPath(), targetFilePath)) {
                log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} renamed to \{targetFilePath.toString()}");
                if (configuration.isRenameRelatedFiles()) {
                    renameRelatedFiles(procFile, oldFilename, targetFilePath.getFileName().toString(), targetFolderPath);
                }

                procFile.setRenamedFile(targetFilePath);
                return true;
            }
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            log.severe(STR."Renaming failed for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}: \{ex.getMessage()}");
            return false;
        }
    }

    private Pair<Path, TagSystemResult> getTargetFolder(FileInfo procFile) throws Exception {
        val configuration = procFile.getConfiguration();
        if (!configuration.isEnableFileMove()) {
            return Pair.of(procFile.getFile().getParentFile().toPath(), null);
        }

        if (configuration.isMoveTypeUseFolder()) {
            val moveToFolder = configuration.getMoveToFolder();
            return Pair.of(moveToFolder.isEmpty() ? procFile.getFile().getParentFile().toPath() : Paths.get(moveToFolder), null);
        }

        val tagSystemResult = getPathFromTagSystem(procFile);
        if (tagSystemResult == null) {
            log.severe(STR."TagSystem script failed for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}. Check your tag system code.");
            return Pair.of(null, null);
        }

        val pathName = tagSystemResult.PathName();
        if (pathName == null) {
            return Pair.of(procFile.getFile().getParentFile().toPath(), tagSystemResult);
        }

        if (pathName.length() > 240) {
            throw new Exception("Pathname too long");
        }

        val targetFolder = Paths.get(pathName);
        if (!targetFolder.isAbsolute()) {
            log.warning(STR."Folderpath for moving from TagSystem needs to be absolute but is \{targetFolder.toString()}");
            return Pair.of(null, tagSystemResult);
        }

        return Pair.of(targetFolder, tagSystemResult);
    }

    private void renameRelatedFiles(FileInfo procFile, String oldFilename, String newFilename, Path folderPath) {
        try {
            val srcFolder = procFile.getFile().getParentFile();
            val oldFilenameWithoutExtension = oldFilename.substring(0, oldFilename.lastIndexOf("."));
            val srcFiles = srcFolder.listFiles((file) -> file.getName().startsWith(oldFilenameWithoutExtension) && !file.getName().equals(oldFilename));

            val relatedFileSuffixes = new HashSet<String>();

            String newFn = newFilename.substring(0, newFilename.lastIndexOf("."));
            for (File srcFile : srcFiles) {
                val filename = srcFile.getName().substring(oldFilenameWithoutExtension.length());
                val relatedSuffix = filename.substring(0, oldFilenameWithoutExtension.length());
                if (fileRenamer.renameFile(srcFile.toPath(), folderPath.resolve(newFn + relatedSuffix))) {
                    relatedFileSuffixes.add(relatedSuffix);
                }
            }
            if (!relatedFileSuffixes.isEmpty()) {
                log.fine(STR."Reanmed related files for \{procFile.getFile().getAbsolutePath()} with suffixes: \{String.join(", ", relatedFileSuffixes)}");
            }
        } catch (Exception e) {
            log.severe(STR."Failed to rename related files for \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}: \{e.getMessage()}");
        }
    }

    private TagSystemResult getPathFromTagSystem(FileInfo procFile) throws Exception {
        val tags = new HashMap<>(procFile.getData());
        val configuration = procFile.getConfiguration();
        tags.put(TagSystemTags.BaseTvShowPath, configuration.getTvShowFolder());
        tags.put(TagSystemTags.BaseMoviePath, configuration.getMovieFolder());
        tags.put(TagSystemTags.FileCurrentFilename, procFile.getFile().getName());

        String codeStr = configuration.getTagSystemCode();
        if (codeStr == null || codeStr.isEmpty()) {
            return null;
        }

        return TagSystem.Evaluate(codeStr, tags);
    }

    public void addFiles(Collection<File> newFiles) {
        addFiles(newFiles, configuration);
    }

    public void addFiles(Collection<File> newFiles, AniConfiguration configuration) {
        Boolean watched = configuration.isSetWatched() ? true : null;
        boolean rename = configuration.isRenameFiles();
        boolean addToMyList = configuration.isAddToMylist();

        for (File cf : newFiles) {
            if (files.contains(KeyType.Path, cf.getAbsolutePath())) {
                log.info(STR."File \{cf.getAbsolutePath()} already in processing/processed");
                continue;
            }

            FileInfo fileInfo = new FileInfo(cf, lastFileId);
            fileInfo.addTodo(FileAction.Process);
            if (configuration.isRenameFiles() || configuration.isEnableFileMove()) {
                fileInfo.addTodo(FileAction.FileCmd);
            }
            fileInfo.setConfiguration(configuration);

            if (addToMyList) {
                fileInfo.addTodo(FileAction.MyListCmd);
            }
            if (rename) {
                fileInfo.addTodo(FileAction.Rename);
            }

            fileInfo.setWatched(watched);

            files.put(fileInfo);
            lastFileId++;
        }
        log.fine(STR."File Count changed to \{files.size()}");
    }

    @Override
    public void start() {
        isProcessing = true;
        log.info("Starting processing");
        processEps();
    }

    public void Terminate() {
        isProcessing = false;
        shouldShutdown = true;
    }

    public enum ProcessingEvent {
        Done
    }

    public static Integer GetFileVersion(int state) {
        int verFlag = (state & (4 + 8 + 16 + 32)) >> 2;
        int version = 1;

        while (verFlag != 0) {
            version++;
            verFlag = verFlag >> 1;
        }

        return version;
    }
}

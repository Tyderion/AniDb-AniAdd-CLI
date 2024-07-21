package processing;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import aniAdd.config.AniConfiguration;
import aniAdd.misc.ICallBack;
import fileprocessor.FileProcessor;
import lombok.extern.java.Log;
import lombok.val;
import processing.FileInfo.eAction;

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
            procFile.actionDone(eAction.Process);
            log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} has been hashed");

            boolean sendML = procFile.isActionTodo(eAction.MyListCmd);
            boolean sendFile = procFile.isActionTodo(eAction.FileCmd);

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
            procFile.actionFailed(eAction.Process);
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
            procFile.actionFailed(eAction.FileCmd);
            val errorMessage = switch (replyStatus) {
                case NO_SUCH_FILE -> "File not found";
                case ILLEGAL_INPUT_OR_ACCESS_DENIED -> "Illegal input or access denied";
                case MULTIPLE_FILES_FOUND -> "Multiple files found";
                default -> "Unknown error";
            };
            log.warning(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} returned error: \{errorMessage}");
            if (replyStatus == ReplyStatus.NO_SUCH_FILE && configuration.isMoveUnknownFiles()) {
                // File not found in anidb
                File currentFile = procFile.getFile();
                val unknownTargetPath = Paths.get(configuration.getUnknownFolder(), currentFile.getParentFile().getName(), currentFile.getName());
                fileRenamer.renameFile(currentFile.toPath(), unknownTargetPath);
            }
        } else {
            procFile.actionDone(eAction.FileCmd);
            query.getCommand().AddReplyToDict(procFile.getData(), query.getReply(), procFile.getWatched());
            log.fine(STR."Got DB Info for file \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}");
        }

        if (!procFile.isFinal() && !(procFile.isActionTodo(eAction.FileCmd) || (procFile.isActionTodo(eAction.MyListCmd)))) {
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
            //File Added/Edited
            procFile.actionDone(eAction.MyListCmd);
            log.info(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} successfully added/edited on MyList");

        } else if (replyStatus == ReplyStatus.FILE_ALREADY_IN_MYLIST) {
            //File Already Added

            if (configuration.isOverwriteMLEntries()) {
                api.queueCommand(query.getCommand().WithEdit());
                log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} already added on MyList, retrying with edit");
            } else {
                log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} already added on MyList");
            }
        } else {
            procFile.actionFailed(eAction.MyListCmd);
            if (replyStatus == ReplyStatus.NO_SUCH_FILE
                    || replyStatus == ReplyStatus.NO_SUCH_ANIME
                    || replyStatus == ReplyStatus.NO_SUCH_GROUP) {
                log.warning(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} returned not found status");
            } else {
                log.warning(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} returned error");
            }
        }

        if (!procFile.isFinal() && !(procFile.isActionTodo(eAction.FileCmd) || (procFile.isActionTodo(eAction.MyListCmd)))) {
            finalProcessing(procFile);
        }
    }

    private void finalProcessing(FileInfo procFile) {
        procFile.setFinal(true);

        if (procFile.isActionTodo(eAction.Rename) && procFile.isActionDone(eAction.FileCmd)) {

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
                    procFile.actionDone(eAction.Rename);
                } else {
                    procFile.actionFailed(eAction.Rename);
                }
            } catch (Exception e) {
            }

            synchronized (this) {
                filesBeingMoved--;
            }
        }

        log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} done");
        // TODO: Remove file from list maybe?
//        files.remove("Id", procFile.getId());
        if (procFile.getId() == lastFileId - 1) {
            sendEvent(ProcessingEvent.Done);
        }
    }

    private boolean renameFile(FileInfo procFile) { //Todo: refractor into smaller Methods
        String filename = "";
        val configuration = procFile.getConfiguration();
        try {
            TagSystemResult ts = null;

            File folderObj = null;

            if (configuration.isEnableFileMove()) {
                if (configuration.isMoveTypeUseFolder()) {
                    // TODO: Remove this functionality
                    folderObj = new File(configuration.getMovieFolder());
                } else {
                    ts = getPathFromTagSystem(procFile);
                    if (ts == null) {
                        log.severe(STR."TagSystem script failed for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}");
                        return false;
                    }
                    String folderStr = ts.PathName();
                    folderObj = new File(folderStr == null ? "" : folderStr);
                }

                if (folderObj.getPath().equals("")) {
                    folderObj = new File(procFile.getFile().getParent());
                } else if (folderObj.getPath().length() > 240) {
                    throw new Exception("Pathname (Folder) too long");
                }
                if (!folderObj.isAbsolute()) {
                    log.warning(STR."Renaming failed for File \{procFile.getFile().getAbsolutePath()}. Folderpath from TagSystem needs to be absolute but is \{folderObj.getPath()}");
                    return false;
                }

                if (!folderObj.isDirectory()) {
                    folderObj.mkdirs();
                }

            } else {
                folderObj = procFile.getFile().getParentFile();
            }

            String ext = procFile.getFile().getName().substring(procFile.getFile().getName().lastIndexOf("."));
            if (!configuration.isEnableFileRenaming()) {
                filename = procFile.getFile().getName();
            } else if (configuration.isRenameTypeAniDBFileName()) {
                filename = procFile.getData().get(TagSystemTags.FileAnidbFilename);
            } else {
                if (ts == null) {
                    ts = getPathFromTagSystem(procFile);
                }
                if (ts == null) {
                    log.severe(STR."TagSystem script failed for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}");
                    return false;
                }
                filename = ts.FileName() + ext;

            }
            filename = filename.replaceAll("[\\\\:\"/*|<>?]", "");

            if (filename.length() + folderObj.getPath().length() > 240) {
                filename = filename.substring(0, 240 - folderObj.getPath().length() - ext.length()) + ext;
            }

            File renFile = new File(folderObj, filename);
            if (renFile.exists() && !(renFile.getParentFile().equals(procFile.getFile().getParentFile()))) {
                log.info(STR."Destination for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} already exists: \{renFile.getAbsolutePath()}");
                if (configuration.isDeleteDuplicateFiles()) {
                    fileRenamer.deleteFile(procFile.getFile().toPath());
                } else if (configuration.isMoveDuplicateFiles()){
                    fileRenamer.renameFile(procFile.getFile().toPath(), Paths.get(configuration.getDuplicatesFolder(), renFile.getParentFile().getName(), renFile.getName()));
                }
                return false;
            } else if (renFile.getAbsolutePath().equals(procFile.getFile().getAbsolutePath())) {
                log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} does not need renaming.");
                return true;
            } else {
                final String oldFilenameWoExt = procFile.getFile().getName().substring(0, procFile.getFile().getName().lastIndexOf("."));

                if (fileRenamer.renameFile(procFile.getFile().toPath(), renFile.toPath())) {
                    log.fine(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} renamed to \{renFile.getAbsolutePath()}");
                    if (configuration.isRenameRelatedFiles()) {
                        renameRelatedFiles(procFile, oldFilenameWoExt, ext, renFile.getName(), renFile.getParentFile());
                    }

                    procFile.setRenamedFile(renFile);
                    return true;

                }
                return false;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            log.severe(STR."Renaming failed for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}: \{ex.getMessage()}");
            return false;
        }
    }

    private void renameRelatedFiles(FileInfo procFile, String oldFilenameWoExt, String ext, String filename, File folderObj) {
        try {

            File srcFolder = procFile.getFile().getParentFile();
            File[] srcFiles = srcFolder.listFiles((dir, name) -> name.startsWith(oldFilenameWoExt) && !name.equals(oldFilenameWoExt + ext));

            String relExt, accumExt = "";
            String newFn = filename.substring(0, filename.lastIndexOf("."));
            for (File srcFile : srcFiles) {
                relExt = srcFile.getName().substring(oldFilenameWoExt.length());
                if (fileRenamer.renameFile(srcFile.toPath(), new File(folderObj, newFn + relExt).toPath())) {
                    accumExt += relExt + " ";
                } else {
                    //Todo
                }
            }
            if (!accumExt.isEmpty()) {
                log.fine(STR."Reanmed related files for \{procFile.getFile().getAbsolutePath()} with extensions: \{accumExt}");
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
            fileInfo.addTodo(eAction.Process);
            if (configuration.isRenameFiles() || configuration.isEnableFileMove()) {
                fileInfo.addTodo(eAction.FileCmd);
            }
            fileInfo.setConfiguration(configuration);

            if (addToMyList) {
                fileInfo.addTodo(eAction.MyListCmd);
            }
            if (rename) {
                fileInfo.addTodo(eAction.Rename);
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

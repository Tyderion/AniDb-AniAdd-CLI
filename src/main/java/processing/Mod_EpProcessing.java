package processing;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import aniAdd.config.AniConfiguration;
import aniAdd.misc.ICallBack;
import fileprocessor.FileProcessor;
import lombok.extern.java.Log;
import lombok.val;
import processing.FileInfo.eAction;

import aniAdd.misc.MultiKeyDict;

import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

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
            (type, fileInfo) -> type == KeyType.Id ? fileInfo.Id() : (type == KeyType.Path ? fileInfo.FileObj().getAbsolutePath() : null));

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
            if (!procFile.Served()) {
                procFile.Served(true);
                log.fine(STR."Processing file \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()}");

                while (filesBeingMoved > 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                    }
                }

                executorService.execute(new FileParser(procFile.FileObj(), procFile.Id(), this::onHashComputed, () -> shouldShutdown));

                return;
            }
        }
        isProcessing = false;
        log.info("Initial Processing done");
    }

    private void onHashComputed(Integer tag, String hash) {
        FileInfo procFile = files.get(KeyType.Id, tag);


        if (procFile != null && hash != null) {
            procFile.Data().put("Ed2k", hash);
            procFile.ActionsDone().add(eAction.Process);
            procFile.ActionsTodo().remove(eAction.Process);
            log.fine(STR."File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} has been hashed");

            boolean sendML = procFile.ActionsTodo().contains(eAction.MyListCmd);
            boolean sendFile = procFile.ActionsTodo().contains(eAction.FileCmd);


            if (sendFile) {
                api.queueCommand(FileCommand.Create(procFile.Id(), procFile.FileObj().length(), procFile.Data().get("Ed2k")));
            }
            if (sendML) {
                api.queueCommand(MylistAddCommand.Create(
                        procFile.Id(),
                        procFile.FileObj().length(),
                        procFile.Data().get("Ed2k"),
                        procFile.MLStorage().ordinal(),
                        procFile.Watched() != null && procFile.Watched()));
            }

            log.fine(STR."Requested Data for file with Id \{procFile.Id()}: SendFile: \{sendFile}, SendML: \{sendML}");

        } else if (procFile != null) {
            procFile.ActionsError().add(eAction.Process);
            procFile.ActionsTodo().remove(eAction.Process);
            log.warning(STR."File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} could not be hashed");
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
        procFile.ActionsTodo().remove(eAction.FileCmd);
        val replyStatus = query.getReply().getReplyStatus();
        if (replyStatus == ReplyStatus.NO_SUCH_FILE
                || replyStatus == ReplyStatus.ILLEGAL_INPUT_OR_ACCESS_DENIED
                || replyStatus == ReplyStatus.MULTIPLE_FILES_FOUND) {
            procFile.ActionsError().add(eAction.FileCmd);
            val errorMessage = switch (replyStatus) {
                case NO_SUCH_FILE -> "File not found";
                case ILLEGAL_INPUT_OR_ACCESS_DENIED -> "Illegal input or access denied";
                case MULTIPLE_FILES_FOUND -> "Multiple files found";
                default -> "Unknown error";
            };
            log.warning(STR."File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} returned error: \{errorMessage}");
            if (replyStatus == ReplyStatus.NO_SUCH_FILE) {
                // File not found in anidb
                File currentFile = procFile.FileObj();
                String unknownFolderPath = STR."/unknown/\{currentFile.getParentFile().getName()}";
//                appendToPostProcessingScript(STR."mkdir -p \"\{unknownFolderPath}\"");
                fileRenamer.renameFile(currentFile.toPath(), Paths.get("/unknown/", currentFile.getParentFile().getName(), currentFile.getName()));
            }
        } else {
            procFile.ActionsDone().add(eAction.FileCmd);
            query.getCommand().AddReplyToDict(procFile.Data(), query.getReply());
            log.fine(STR."Got DB Info for file \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()}");
        }

        if (!procFile.IsFinal() && !(procFile.ActionsTodo().contains(eAction.FileCmd) || (procFile.ActionsTodo().contains(eAction.MyListCmd)))) {
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
        procFile.ActionsTodo().remove(eAction.MyListCmd);

        if (replyStatus == ReplyStatus.MYLIST_ENTRY_ADDED
                || replyStatus == ReplyStatus.MYLIST_ENTRY_EDITED) {
            //File Added/Edited
            procFile.ActionsDone().add(eAction.MyListCmd);
            log.info(STR."File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} successfully added/edited on MyList");

        } else if (replyStatus == ReplyStatus.FILE_ALREADY_IN_MYLIST) {
            //File Already Added

            if (configuration.isOverwriteMLEntries()) {
                procFile.ActionsTodo().add(eAction.MyListCmd);
                api.queueCommand(query.getCommand().WithEdit());
                log.fine(STR."File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} already added on MyList, retrying with edit");
            } else {
                log.fine(STR."File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} already added on MyList");
            }
        } else {
            procFile.ActionsError().add(eAction.MyListCmd);
            if (replyStatus == ReplyStatus.NO_SUCH_FILE
                    || replyStatus == ReplyStatus.NO_SUCH_ANIME
                    || replyStatus == ReplyStatus.NO_SUCH_GROUP) {
                log.warning(STR."File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} returned not found status");
            } else {
                log.warning(STR."File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} returned error");
            }
        }

        if (!procFile.IsFinal() && !(procFile.ActionsTodo().contains(eAction.FileCmd) || (procFile.ActionsTodo().contains(eAction.MyListCmd)))) {
            finalProcessing(procFile);
        }
    }

    private void finalProcessing(FileInfo procFile) {
        procFile.IsFinal(true);

        if (procFile.ActionsTodo().contains(eAction.Rename) && procFile.ActionsDone().contains(eAction.FileCmd)) {
            procFile.ActionsTodo().remove(eAction.Rename);


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
                    procFile.ActionsDone().add(eAction.Rename);
                } else {
                    procFile.ActionsError().add(eAction.Rename);
                }
            } catch (Exception e) {
            }

            synchronized (this) {
                filesBeingMoved--;
            }
        }

        log.fine(STR."File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} done");
        // TODO: Remove file from list maybe?
//        files.remove("Id", procFile.Id());
        if (procFile.Id() == lastFileId - 1) {
            sendEvent(ProcessingEvent.Done);
        }
    }

    private boolean renameFile(FileInfo procFile) { //Todo: refractor into smaller Methods
        String filename = "";
        val configuration = procFile.getConfiguration();
        try {
            TreeMap<String, String> ts = null;

            File folderObj = null;

            if (configuration.isEnableFileMove()) {
                if (configuration.isMoveTypeUseFolder()) {
                    // TODO: Remove this functionality
                    folderObj = new File(configuration.getMovieFolder());
                } else {
                    ts = getPathFromTagSystem(procFile);
                    if (ts == null) {
                        log.severe(STR."TagSystem script failed for File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()}");
                    }

                    String folderStr = ts.get("PathName");
                    folderObj = new File(folderStr == null ? "" : folderStr);
                }

                if (folderObj.getPath().equals("")) {
                    folderObj = new File(procFile.FileObj().getParent());
                } else if (folderObj.getPath().length() > 240) {
                    throw new Exception("Pathname (Folder) too long");
                }
                if (!folderObj.isAbsolute()) {
                    log.warning(STR."Renaming failed for File \{procFile.FileObj().getAbsolutePath()}. Folderpath from TagSystem needs to be absolute but is \{folderObj.getPath()}");
                    return false;
                }

                if (!folderObj.isDirectory()) {
                    folderObj.mkdirs();
                }

            } else {
                folderObj = procFile.FileObj().getParentFile();
            }

            String ext = procFile.FileObj().getName().substring(procFile.FileObj().getName().lastIndexOf("."));
            if (!configuration.isEnableFileRenaming()) {
                filename = procFile.FileObj().getName();
            } else if (configuration.isRenameTypeAniDBFileName()) {
                filename = procFile.Data().get("DB_FileName");
            } else {
                if (ts == null) {
                    ts = getPathFromTagSystem(procFile);
                }
                if (ts == null) {
                    log.severe(STR."TagSystem script failed for File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()}");
                }

                filename = ts.get("FileName") + ext;
            }
            filename = filename.replaceAll("[\\\\:\"/*|<>?]", "");

            if (filename.length() + folderObj.getPath().length() > 240) {
                filename = filename.substring(0, 240 - folderObj.getPath().length() - ext.length()) + ext;
            }

            File renFile = new File(folderObj, filename);
            if (renFile.exists() && !(renFile.getParentFile().equals(procFile.FileObj().getParentFile()))) {
                log.info(STR."Destination for File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} already exists: \{renFile.getAbsolutePath()}");
                if (configuration.isDeleteDuplicateFiles()) {
                    fileRenamer.deleteFile(procFile.FileObj().toPath());
                } else {
//                    appendToPostProcessingScript(STR."mkdir -p \"/duplicates/\{renFile.getParentFile().getName()}\"");
                    fileRenamer.renameFile(procFile.FileObj().toPath(), Paths.get("/duplicates/", renFile.getParentFile().getName(), renFile.getName()));
                }
                return false;
            } else if (renFile.getAbsolutePath().equals(procFile.FileObj().getAbsolutePath())) {
                log.fine(STR."File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} does not need renaming.");
                return true;
            } else {
                final String oldFilenameWoExt = procFile.FileObj().getName().substring(0, procFile.FileObj().getName().lastIndexOf("."));

                if (fileRenamer.renameFile(procFile.FileObj().toPath(), renFile.toPath())) {
                    log.fine(STR."File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()} renamed to \{renFile.getAbsolutePath()}");
                    if (configuration.isRenameRelatedFiles()) {
                        renameRelatedFiles(procFile, oldFilenameWoExt, ext, renFile.getName(), renFile.getParentFile());
                    }

                    procFile.FileObj(renFile);
                    return true;

                }
                return false;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            log.severe(STR."Renaming failed for File \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()}: \{ex.getMessage()}");
            return false;
        }
    }

    private void renameRelatedFiles(FileInfo procFile, String oldFilenameWoExt, String ext, String filename, File folderObj) {
        try {

            File srcFolder = procFile.FileObj().getParentFile();
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
                log.fine(STR."Reanmed related files for \{procFile.FileObj().getAbsolutePath()} with extensions: \{accumExt}");
            }
        } catch (Exception e) {
            log.severe(STR."Failed to rename related files for \{procFile.FileObj().getAbsolutePath()} with Id \{procFile.Id()}: \{e.getMessage()}");
        }
    }

    private TreeMap<String, String> getPathFromTagSystem(FileInfo procFile) throws Exception {
        TreeMap<String, String> tags = new TreeMap<String, String>();
        val configuration = procFile.getConfiguration();
        tags.put("BaseTVShowPath", configuration.getTvShowFolder());
        tags.put("BaseMoviePath", configuration.getMovieFolder());

        tags.put("ATr", procFile.Data().get("DB_SN_Romaji"));
        tags.put("ATe", procFile.Data().get("DB_SN_English"));
        tags.put("ATk", procFile.Data().get("DB_SN_Kanji"));
        tags.put("ATs", procFile.Data().get("DB_Synonym"));
        tags.put("ATo", procFile.Data().get("DB_SN_Other"));

        String[] year = procFile.Data().get("DB_Year").split("-", -1);
        tags.put("AYearBegin", year[0]);
        if (year.length == 2) {
            tags.put("AYearEnd", year[1]);
        }

        tags.put("ACatList", procFile.Data().get("DB_CatList"));

        tags.put("ETr", procFile.Data().get("DB_EpN_Romaji"));
        tags.put("ETe", procFile.Data().get("DB_EpN_English"));
        tags.put("ETk", procFile.Data().get("DB_EpN_Kanji"));
        tags.put("EAirDate", procFile.Data().get("DB_AirDate"));

        tags.put("GTs", procFile.Data().get("DB_Group_Short"));
        tags.put("GTl", procFile.Data().get("DB_Group_Long"));

        tags.put("FCrc", procFile.Data().get("DB_CRC"));
        tags.put("FALng", procFile.Data().get("DB_FileAudioLang"));
        tags.put("FACodec", procFile.Data().get("DB_AudioCodec"));
        tags.put("FSLng", procFile.Data().get("DB_FileSubLang"));
        tags.put("FVCodec", procFile.Data().get("DB_VideoCodec"));
        tags.put("FVideoRes", procFile.Data().get("DB_VideoRes"));
        tags.put("FColorDepth", procFile.Data().get("DB_ColorDepth"));
        tags.put("FDuration", procFile.Data().get("DB_Duration"));

        tags.put("AniDBFN", procFile.Data().get("DB_FileName"));
        tags.put("CurrentFN", procFile.FileObj().getName());

        tags.put("EpNo", procFile.Data().get("DB_EpNo"));
        tags.put("EpHiNo", procFile.Data().get("DB_EpHiCount"));
        tags.put("EpCount", procFile.Data().get("DB_EpCount"));

        tags.put("FId", procFile.Data().get("DB_FId"));
        tags.put("AId", procFile.Data().get("DB_AId"));
        tags.put("EId", procFile.Data().get("DB_EId"));
        tags.put("GId", procFile.Data().get("DB_GId"));
        tags.put("LId", procFile.Data().get("DB_LId"));

        tags.put("OtherEps", procFile.Data().get("DB_OtherEps"));

        tags.put("Quality", procFile.Data().get("DB_Quality"));
        tags.put("Source", procFile.Data().get("DB_Source"));
        tags.put("Type", procFile.Data().get("DB_Type"));

        if (procFile.ActionsDone().contains(eAction.MyListCmd)) {
            tags.put("Watched", (procFile.Watched() != null && procFile.Watched() || procFile.Watched() == null && procFile.Data().get("DB_IsWatched").equals("1")) ? "1" : "");
        } else {
            tags.put("Watched", procFile.Data().get("DB_IsWatched").equals("1") ? "1" : "");
        }

        tags.put("Depr", procFile.Data().get("DB_Deprecated").equals("1") ? "1" : "");
        tags.put("CrcOK", ((Integer.valueOf(procFile.Data().get("DB_State")) & 1 << 0) != 0 ? "1" : ""));
        tags.put("CrcErr", ((Integer.valueOf(procFile.Data().get("DB_State")) & 1 << 1) != 0 ? "1" : ""));
        tags.put("Cen", ((Integer.valueOf(procFile.Data().get("DB_State")) & 1 << 7) != 0 ? "1" : ""));
        tags.put("UnCen", ((Integer.valueOf(procFile.Data().get("DB_State")) & 1 << 6) != 0 ? "1" : ""));
        tags.put("Ver", GetFileVersion(Integer.valueOf(procFile.Data().get("DB_State"))).toString());

        String codeStr = configuration.getTagSystemCode();
        if (codeStr == null || codeStr.isEmpty()) {
            return null;
        }

        TagSystem.Evaluate(codeStr, tags);

        return tags;
    }

    public void addFiles(Collection<File> newFiles) {
        addFiles(newFiles, configuration);
    }

    public void addFiles(Collection<File> newFiles, AniConfiguration configuration) {
        Boolean watched = configuration.isSetWatched() ? true : null;

        int storage = configuration.getSetStorageType().getValue();
        boolean rename = configuration.isRenameFiles();
        boolean addToMyList = configuration.isAddToMylist();

        for (File cf : newFiles) {
            if (files.contains(KeyType.Path, cf.getAbsolutePath())) {
                log.info(STR."File \{cf.getAbsolutePath()} already in processing/processed");
                continue;
            }

            FileInfo fileInfo = new FileInfo(cf, lastFileId);
            fileInfo.MLStorage(FileInfo.eMLStorageState.values()[storage]);
            fileInfo.ActionsTodo().add(eAction.Process);
            if (configuration.isRenameFiles() || configuration.isEnableFileMove()) {
                fileInfo.ActionsTodo().add(eAction.FileCmd);
            }
            fileInfo.setConfiguration(configuration);

            if (addToMyList) {
                fileInfo.ActionsTodo().add(eAction.MyListCmd);
            }
            if (rename) {
                fileInfo.ActionsTodo().add(eAction.Rename);
            }

            fileInfo.Watched(watched);

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

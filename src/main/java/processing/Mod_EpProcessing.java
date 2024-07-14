package processing;

import java.io.*;
import java.util.Collection;

import aniAdd.Modules.BaseModule;
import aniAdd.config.AniConfiguration;
import lombok.val;
import processing.FileInfo.eAction;

import aniAdd.IAniAdd;
import aniAdd.misc.ICallBack;
import aniAdd.misc.MultiKeyDict;
import aniAdd.misc.MultiKeyDict.IKeyMapper;

import java.util.TreeMap;

import udpapi2.UdpApi;
import udpapi2.command.FileCommand;
import udpapi2.command.LogoutCommand;
import udpapi2.command.MylistAddCommand;
import udpapi2.query.Query;
import udpapi2.reply.ReplyStatus;

public class Mod_EpProcessing extends BaseModule {

    public static String[] supportedFiles = {"avi", "ac3", "mpg", "mpeg", "rm", "rmvb", "asf", "wmv", "mov", "ogm", "mp4", "mkv", "rar", "zip", "ace", "srt", "sub", "ssa", "smi", "idx", "ass", "txt", "swf", "flv"};
    private UdpApi api;
    private FileParser fileParser;
    private boolean isProcessing;
    private boolean isPaused;
    private int lastFileId = 0;
    private int filesBeingMoved;
    private final AniConfiguration configuration;

    private final MultiKeyDict<String, Object, FileInfo> files = new MultiKeyDict<>(new IKeyMapper<String, Object, FileInfo>() {

        public int count() {
            return 2;
        }

        public int getCatIndex(String category) {
            return category.equals("Id") ? 0 : (category.equals("Path") ? 1 : -1);
        }

        public Object getKey(int index, FileInfo fileInfo) {
            return index == 0 ? fileInfo.Id() : (index == 1 ? fileInfo.FileObj().getAbsolutePath() : null);
        }
    });

    public Mod_EpProcessing(AniConfiguration configuration, UdpApi udpApi) {
        this.configuration = configuration;
        this.api = udpApi;

        api.registerCallback(FileCommand.class, this::aniDBInfoReply);
        api.registerCallback(MylistAddCommand.class, this::aniDBMyListReply);
    }

    private void processEps() {
        while (isPaused) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
            }
        }

        for (FileInfo procFile : files.values()) {
            if (!procFile.Served()) {
                procFile.Served(true);
                Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.Processing, procFile.Id());

                while (filesBeingMoved > 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                    }
                }

                fileParser = new FileParser(procFile.FileObj(), new ICallBack<FileParser>() {

                    public void invoke(FileParser fileParser) {
                        continueProcessing(fileParser);
                    }
                }, procFile.Id());
                fileParser.start();
                return;
            }
        }
        isProcessing = false;
        Log(CommunicationEvent.EventType.Information, eComType.Status, eComSubType.Done);
    }

    private void continueProcessing(FileParser fileParser) {
        this.fileParser = null;

        FileInfo procFile = files.get("Id", fileParser.Tag());


        if (procFile != null && fileParser.Hash() != null) {
            procFile.Data().put("Ed2k", fileParser.Hash());
            procFile.ActionsDone().add(eAction.Process);
            procFile.ActionsTodo().remove(eAction.Process);

            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.ParsingDone, procFile.Id(), fileParser);

            boolean sendML = procFile.ActionsTodo().contains(eAction.MyListCmd);
            boolean sendFile = procFile.ActionsTodo().contains(eAction.FileCmd);


            if (sendFile) {
                requestDBFileInfo(procFile);
            }
            if (sendML) {
                requestDBMyList(procFile);
            }

            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.GetDBInfo, procFile.Id(), sendFile, sendML);

        } else if (procFile != null) {
            procFile.ActionsError().add(eAction.Process);
            procFile.ActionsTodo().remove(eAction.Process);

            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.ParsingError, procFile.Id(), fileParser);
        }

        if (isProcessing) {
            processEps();
        }
    }

    private void requestDBFileInfo(FileInfo procFile) {
        api.queueCommand(FileCommand.Create(procFile.Id(), procFile.FileObj().length(), procFile.Data().get("Ed2k")));
    }

    private void requestDBMyList(FileInfo procFile) {
        api.queueCommand(MylistAddCommand.Create(
                procFile.Id(),
                procFile.FileObj().length(),
                procFile.Data().get("Ed2k"),
                procFile.MLStorage().ordinal(),
                procFile.Watched() != null && procFile.Watched()));
    }

    private void aniDBInfoReply(Query<FileCommand> query) {
        //System.out.println("Got Fileinfo reply");

        int fileId = Integer.parseInt(query.getCommand().getTag());
        if (!files.contains("Id", fileId)) {
            return; //File not found (Todo: throw error)
        }
        FileInfo procFile = files.get("Id", fileId);
        procFile.ActionsTodo().remove(eAction.FileCmd);
        val replyStatus = query.getReply().getReplyStatus();
        if (replyStatus == ReplyStatus.NO_SUCH_FILE
                || replyStatus == ReplyStatus.ILLEGAL_INPUT_OR_ACCESS_DENIED
                || replyStatus == ReplyStatus.MULTIPLE_FILES_FOUND) {
            procFile.ActionsError().add(eAction.FileCmd);
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, replyStatus == ReplyStatus.NO_SUCH_FILE ? eComSubType.FileCmd_NotFound : eComSubType.FileCmd_Error, procFile.Id());
            if (replyStatus == ReplyStatus.NO_SUCH_FILE) {
                // File not found in anidb
                File currentFile = procFile.FileObj();
                String unknownFolderPath = "/unknown/" + currentFile.getParentFile().getName();
                appendToPostProcessingScript("mkdir -p \"" + unknownFolderPath + "\"");
                appendToPostProcessingScript("mv \"" + currentFile.getAbsolutePath() + "\" \"" + unknownFolderPath + "/" + currentFile.getName() + "\"");
            }
        } else {
            procFile.ActionsDone().add(eAction.FileCmd);
            query.getCommand().AddReplyToDict(procFile.Data(), query.getReply());
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.FileCmd_GotInfo, procFile.Id());
        }

        if (!procFile.IsFinal() && !(procFile.ActionsTodo().contains(eAction.FileCmd) || (procFile.ActionsTodo().contains(eAction.MyListCmd)))) {
            finalProcessing(procFile);
        }
    }

    private void aniDBMyListReply(Query<MylistAddCommand> query) {
        //System.out.println("Got ML Reply");
        val replyStatus = query.getReply().getReplyStatus();

        int fileId = Integer.parseInt(query.getCommand().getFullTag());
        if (!files.contains("Id", fileId)) {
            //System.out.println("MLCmd: Id not found");
            return; //File not found (Todo: throw error)
        }
        FileInfo procFile = files.get("Id", fileId);
        val configuration = procFile.getConfiguration();
        procFile.ActionsTodo().remove(eAction.MyListCmd);

        if (replyStatus == ReplyStatus.MYLIST_ENTRY_ADDED
                || replyStatus == ReplyStatus.MYLIST_ENTRY_EDITED) {
            //File Added/Edited
            procFile.ActionsDone().add(eAction.MyListCmd);
            /*if (procFile.ActionsTodo().remove(eAction.SetWatchedState)) {
            procFile.ActionsDone().add(eAction.SetWatchedState);
            }*/
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.MLCmd_FileAdded, procFile.Id());

        } else if (replyStatus == ReplyStatus.FILE_ALREADY_IN_MYLIST) {
            //File Already Added

            if (configuration.isOverwriteMLEntries()) {
                procFile.ActionsTodo().add(eAction.MyListCmd);
                api.queueCommand(((MylistAddCommand) query.getCommand()).WithEdit());
            }

            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.MLCmd_AlreadyAdded, procFile.Id());

        } else {
            procFile.ActionsError().add(eAction.MyListCmd);
            /*if (procFile.ActionsTodo().remove(eAction.SetWatchedState)) {
            procFile.ActionsError().add(eAction.SetWatchedState);
            }*/

            if (replyStatus == ReplyStatus.NO_SUCH_FILE
                    || replyStatus == ReplyStatus.NO_SUCH_ANIME
                    || replyStatus == ReplyStatus.NO_SUCH_GROUP) {
                Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.MLCmd_NotFound, procFile.Id());
            } else {
                Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.MLCmd_Error, procFile.Id());
            }
        }

        if (!procFile.IsFinal() && !(procFile.ActionsTodo().contains(eAction.FileCmd) || (procFile.ActionsTodo().contains(eAction.MyListCmd)))) {
            finalProcessing(procFile);
        }
    }

    private void finalProcessing(FileInfo procFile) {
        System.out.println(STR."Final processing \{procFile.Id()}");
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

        Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.Done, procFile.Id());
        if (procFile.Id() == lastFileId - 1) {
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.Done);
        }
    }

    private boolean renameFile(FileInfo procFile) { //Todo: refractor into smaller Methods
        String filename = "";
        val configuration = procFile.getConfiguration();
        try {
            TreeMap<String, String> ts = null;

            File folderObj = null;

            System.out.println("GUI_EnableFileMove" + configuration.isEnableFileMove());
            if (configuration.isEnableFileMove()) {
                if (configuration.isMoveTypeUseFolder()) {
                    // TODO: Remove this functionality
                    folderObj = new File(configuration.getMovieFolder());
                } else {
                    ts = getPathFromTagSystem(procFile);
                    if (ts == null) {
                        Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RenamingFailed, procFile.Id(), procFile.FileObj(), "TagSystem script failed");
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
                    Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RenamingFailed, procFile.Id(), procFile.FileObj(), "Folderpath needs to be absolute.");
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
                    Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RenamingFailed, procFile.Id(), procFile.FileObj(), "TagSystem script failed");
                }

                filename = ts.get("FileName") + ext;
            }
            filename = filename.replaceAll("[\\\\:\"/*|<>?]", "");

            boolean truncated = false;
            if (filename.length() + folderObj.getPath().length() > 240) {
                filename = filename.substring(0, 240 - folderObj.getPath().length() - ext.length()) + ext;
                truncated = true;
            }


            File renFile = new File(folderObj, filename);
            if (renFile.exists() && !(renFile.getParentFile().equals(procFile.FileObj().getParentFile()))) {
                Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RenamingFailed, procFile.Id(), procFile.FileObj(), "Destination filename already exists.");
                if (configuration.isDeleteDuplicateFiles()) {
                    appendToPostProcessingScript("rm \"" + procFile.FileObj().getAbsolutePath() + "\"");
                } else {
                    appendToPostProcessingScript("mkdir -p \"" + "/duplicates/" + renFile.getParentFile().getName() + "\"");
                    appendToPostProcessingScript("mv \"" + procFile.FileObj().getAbsolutePath() + "\" \"" + "/duplicates/" + renFile.getParentFile().getName() + "/" + renFile.getName() + "\"");
                }
                return false;
            } else if (renFile.getAbsolutePath().equals(procFile.FileObj().getAbsolutePath())) {
                Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RenamingNotNeeded, procFile.Id(), procFile.FileObj());
                return true;
            } else {
                final String oldFilenameWoExt = procFile.FileObj().getName().substring(0, procFile.FileObj().getName().lastIndexOf("."));

                if (renFile.equals(procFile.FileObj())) { // yay java
                    File tmpFile = new File(renFile.getAbsolutePath() + ".tmp");
                    procFile.FileObj().renameTo(tmpFile);
                    procFile.FileObj(tmpFile);
                }

                if (tryRenameFile(procFile.Id(), procFile.FileObj(), renFile)) {
                    Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.FileRenamed, procFile.Id(), renFile, truncated);
                    if (configuration.isRenameRelatedFiles()) {
                        // <editor-fold defaultstate="collapsed" desc="Rename Related Files">
                        try {

                            File srcFolder = procFile.FileObj().getParentFile();

                            File[] srcFiles = srcFolder.listFiles((dir, name) -> name.startsWith(oldFilenameWoExt) && !name.equals(oldFilenameWoExt + ext));

                            String relExt, accumExt = "";
                            String newFn = filename.substring(0, filename.lastIndexOf("."));
                            for (File srcFile : srcFiles) {
                                relExt = srcFile.getName().substring(oldFilenameWoExt.length());
                                if (tryRenameFile(procFile.Id(), srcFile, new File(folderObj, newFn + relExt))) {
                                    accumExt += relExt + " ";
                                } else {
                                    //Todo
                                }
                            }
                            if (!accumExt.isEmpty()) {
                                Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RelFilesRenamed, procFile.Id(), accumExt);
                            }
                        } catch (Exception e) {
                            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RelFilesRenamingFailed, procFile.Id(), e.getMessage());
                        }
                        // </editor-fold>
                    }

                    procFile.FileObj(renFile);
                    return true;

                } else {
                    Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RenamingFailed, "Java Renaming Failed", procFile.Id(), procFile.FileObj(), renFile.getAbsolutePath());
                    return false;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RenamingFailed, procFile.Id(), procFile.FileObj(), ex.getMessage());
            return false;
        }
    }

    private boolean tryRenameFile(int id, File original, File targetFile) {
        if (original.renameTo(targetFile)) {
            return true;
        }
        Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RenamingFailed, "Java Renaming Failed", id, original, targetFile.getAbsolutePath(), "Will rename aftewards via shell script.");
        String command = STR."mv \"\{original.getAbsolutePath()}\" \"\{targetFile.getAbsolutePath()}\"";
        if (!targetFile.getParentFile().exists()) {
            appendToPostProcessingScript(STR."mkdir -p \{targetFile.getParentFile().getAbsolutePath()}"); // Make sure the folder exists
        }
        appendToPostProcessingScript(command);
        return false;
    }

    private void appendToPostProcessingScript(String line) {
        String path = "rename.sh";
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(path, true));

            writer.append(line);
            writer.append("\n");
            writer.close();
        } catch (IOException e) {
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RenamingFailed, "Could not write to move file", e.getMessage());
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
            if (files.contains("Path", cf.getAbsolutePath())) {
                continue;
            }

            FileInfo fileInfo = new FileInfo(cf, lastFileId);
            fileInfo.MLStorage(FileInfo.eMLStorageState.values()[storage]);
            fileInfo.ActionsTodo().add(eAction.Process);
            fileInfo.ActionsTodo().add(eAction.FileCmd);
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

        Log(CommunicationEvent.EventType.Information, eComType.FileCountChanged);
    }

    public void processing(eProcess proc) {
        switch (proc) {
            case Start:
                //System.out.println("Processing started");
                isProcessing = true;
                isPaused = false;
                Log(CommunicationEvent.EventType.Information, eComType.Status, eProcess.Start);
                processEps();

                break;

            case Pause:
                //System.out.println("Processing paused");
                isPaused = true;
                if (fileParser != null) {
                    fileParser.pause();
                }

                Log(CommunicationEvent.EventType.Information, eComType.Status, eProcess.Pause);
                break;

            case Resume:
                //System.out.println("Processing resumed");
                if (fileParser != null) {
                    fileParser.resume();
                }

                isPaused = false;
                Log(CommunicationEvent.EventType.Information, eComType.Status, eProcess.Resume);
                break;

            case Stop:
                //System.out.println("Processing stopped");
                //Not yet supported
                isProcessing = false;
                isPaused = false;
                Log(CommunicationEvent.EventType.Information, eComType.Status, eProcess.Stop);
                break;

        }


    }

    protected String modName = "EpProcessing";
    protected eModState modState = eModState.New;

    public eModState ModState() {
        return modState;
    }

    public String ModuleName() {
        return modName;
    }

    public void Initialize(IAniAdd aniAdd, AniConfiguration configuration) {
        modState = eModState.Initializing;
        lastFileId = 0;
        modState = eModState.Initialized;
    }

    public void Terminate() {
        modState = eModState.Terminating;

        isProcessing = false;
        if (fileParser != null) {
            fileParser.terminate();
        }

        modState = eModState.Terminated;
    }

    public enum eProcess {

        Start, Pause, Resume, Stop
    }

    public enum eComType {

        FileSettings, FileCountChanged, FileEvent, Status
    }

    public enum eComSubType {

        Processing, NoWriteAccess, GotFromHistory, ParsingDone, ParsingError, GetDBInfo, FileCmd_NotFound, FileCmd_GotInfo, FileCmd_Error, MLCmd_FileAdded, MLCmd_AlreadyAdded, MLCmd_FileRemoved, MLCmd_NotFound, MLCmd_Error, VoteCmd_EpVoted, VoteCmd_EpVoteRevoked, VoteCmd_Error, RenamingFailed, FileRenamed, RenamingNotNeeded, RelFilesRenamed, RelFilesRenamingFailed, DeletedEmptyFolder, DeletetingEmptyFolderFailed, Done
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

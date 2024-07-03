package processing;

import java.io.*;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;

import aniAdd.Modules.BaseModule;
import aniAdd.config.AniConfiguration;
import processing.FileInfo.eAction;
import udpApi.Cmd;
import udpApi.Query;

import aniAdd.IAniAdd;
import aniAdd.misc.ICallBack;
import aniAdd.misc.Misc;
import aniAdd.misc.MultiKeyDict;
import aniAdd.misc.MultiKeyDict.IKeyMapper;

import java.util.TreeMap;

import udpApi.Mod_UdpApi;

public class Mod_EpProcessing extends BaseModule {

    public static String[] supportedFiles = {"avi", "ac3", "mpg", "mpeg", "rm", "rmvb", "asf", "wmv", "mov", "ogm", "mp4", "mkv", "rar", "zip", "ace", "srt", "sub", "ssa", "smi", "idx", "ass", "txt", "swf", "flv"};
    private Mod_UdpApi api;
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

    public Mod_EpProcessing(AniConfiguration configuration) {
        this.configuration = configuration;
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
        Cmd cmd = new Cmd("FILE", "file", procFile.Id().toString(), true);

        BitSet binCode = new BitSet(64);
        binCode.set(0); //'state
        binCode.set(1); //'Depr
        binCode.set(2); //'other eps //new
        binCode.set(3); //'lid
        binCode.set(4); //gid
        binCode.set(5); //eid
        binCode.set(6); //aid

        binCode.set(9); //'bit depth //new
        binCode.set(11); //'crc

        binCode.set(17); //'video res
        binCode.set(19); //'VideoCodec
        binCode.set(21); //'AudioCodec
        binCode.set(22); //'Source
        binCode.set(23); //'Quality

        binCode.set(24); //'anidb filename scheme
        binCode.set(27); //'air date //new
        binCode.set(29); //'length in seconds //new
        binCode.set(30); //'sub lang list
        binCode.set(31); //'dub lang list

        binCode.set(37); //'watched state
        cmd.setArgs("fmask", Misc.toMask(binCode, 40));

        binCode = new BitSet(32);
        binCode.set(1); //'category list
        binCode.set(4); //'type
        binCode.set(5); //'year
        binCode.set(6); //'highest EpCount
        binCode.set(7); //'epCount

        binCode.set(10); //'synonym
        binCode.set(11); //'short name
        binCode.set(12); //'other name
        binCode.set(13); //'english name
        binCode.set(14); //'kanji name
        binCode.set(15); //'romaji name

        binCode.set(20); //'ep kanji
        binCode.set(21); //'ep romaji
        binCode.set(22); //'ep name
        binCode.set(23); //'epno

        binCode.set(30); //'group short name
        binCode.set(31); //'group name
        cmd.setArgs("amask", Misc.toMask(binCode, 32));
        cmd.setArgs("size", Long.toString(procFile.FileObj().length()));
        cmd.setArgs("ed2k", procFile.Data().get("Ed2k"));

        api.queryCmd(cmd);
        //System.out.println("Sending File Cmd");
    }

    private void requestDBMyList(FileInfo procFile) {
        Cmd cmd = new Cmd("MYLISTADD", "mladd", procFile.Id().toString(), true);
        cmd.setArgs("size", Long.toString(procFile.FileObj().length()));
        cmd.setArgs("ed2k", (String) procFile.Data().get("Ed2k"));
        cmd.setArgs("state", Integer.toString(procFile.MLStorage().ordinal()));

        if (procFile.Watched() != null) {
            cmd.setArgs("viewed", procFile.Watched() ? "1" : "0");
        }

        api.queryCmd(cmd);
        //System.out.println("Sending ML Cmd");
    }

    private void aniDBInfoReply(int queryId) {
        //System.out.println("Got Fileinfo reply");

        Query query = api.Queries().get(queryId);
        int replyId = query.getReply().ReplyId();

        int fileId = Integer.parseInt(query.getReply().Tag());
        if (!files.contains("Id", fileId)) {
            return; //File not found (Todo: throw error)
        }
        FileInfo procFile = files.get("Id", fileId);
        procFile.ActionsTodo().remove(eAction.FileCmd);

        if (replyId == 320 || replyId == 505 || replyId == 322) {
            procFile.ActionsError().add(eAction.FileCmd);
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, replyId == 320 ? eComSubType.FileCmd_NotFound : eComSubType.FileCmd_Error, procFile.Id());
            if (replyId == 320) {
                // File not found in anidb
                File currentFile = procFile.FileObj();
                String unknownFolderPath = "unknown/" + currentFile.getParentFile().getName();
                appendToPostProcessingScript("mkdir -p \"" + unknownFolderPath + "\"");
                appendToPostProcessingScript("mv \"" + currentFile.getAbsolutePath() + "\" \"" + unknownFolderPath + "/" + currentFile.getName() + "\"");
            }
        } else {
            procFile.ActionsDone().add(eAction.FileCmd);
            ArrayDeque<String> df = new ArrayDeque<String>(query.getReply().DataField());
            procFile.Data().put("DB_FId", df.poll());
            procFile.Data().put("DB_AId", df.poll());
            procFile.Data().put("DB_EId", df.poll());
            procFile.Data().put("DB_GId", df.poll());
            procFile.Data().put("DB_LId", df.poll());
            procFile.Data().put("DB_OtherEps", df.poll());
            procFile.Data().put("DB_Deprecated", df.poll());
            procFile.Data().put("DB_State", df.poll());
            procFile.Data().put("DB_CRC", df.poll());
            procFile.Data().put("DB_ColorDepth", df.poll());
            procFile.Data().put("DB_Quality", df.poll());
            procFile.Data().put("DB_Source", df.poll());
            procFile.Data().put("DB_AudioCodec", df.poll());
            procFile.Data().put("DB_VideoCodec", df.poll());
            procFile.Data().put("DB_VideoRes", df.poll());
            procFile.Data().put("DB_FileAudioLang", df.poll());
            procFile.Data().put("DB_FileSubLang", df.poll());
            procFile.Data().put("DB_Duration", df.poll());
            procFile.Data().put("DB_AirDate", df.poll());
            procFile.Data().put("DB_FileName", df.poll());
            procFile.Data().put("DB_IsWatched", df.poll());

            procFile.Data().put("DB_EpCount", df.poll());
            procFile.Data().put("DB_EpHiCount", df.poll());
            procFile.Data().put("DB_Year", df.poll());
            procFile.Data().put("DB_Type", df.poll());
            procFile.Data().put("DB_CatList", df.poll());
            procFile.Data().put("DB_SN_Romaji", df.poll());
            procFile.Data().put("DB_SN_Kanji", df.poll());
            procFile.Data().put("DB_SN_English", df.poll());
            procFile.Data().put("DB_SN_Other", df.poll());
            procFile.Data().put("DB_SN_Short", df.poll());
            procFile.Data().put("DB_SN_Synonym", df.poll());
            procFile.Data().put("DB_EpNo", df.poll());
            procFile.Data().put("DB_EpN_English", df.poll());
            procFile.Data().put("DB_EpN_Romaji", df.poll());
            procFile.Data().put("DB_EpN_Kanji", df.poll());
            procFile.Data().put("DB_Group_Long", df.poll());
            procFile.Data().put("DB_Group_Short", df.poll());
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.FileCmd_GotInfo, procFile.Id());
        }

        if (!procFile.IsFinal() && !(procFile.ActionsTodo().contains(eAction.FileCmd) || (procFile.ActionsTodo().contains(eAction.MyListCmd)))) {
            finalProcessing(procFile);
        }
    }

    private void aniDBMyListReply(int queryId) {
        //System.out.println("Got ML Reply");

        Query query = api.Queries().get(queryId);
        int replyId = query.getReply().ReplyId();

        int fileId = Integer.parseInt(query.getReply().Tag());
        if (!files.contains("Id", fileId)) {
            //System.out.println("MLCmd: Id not found");
            return; //File not found (Todo: throw error)
        }
        FileInfo procFile = files.get("Id", fileId);
        procFile.ActionsTodo().remove(eAction.MyListCmd);

        if (replyId == 210 || replyId == 311) {
            //File Added/Edited
            procFile.ActionsDone().add(eAction.MyListCmd);
            /*if (procFile.ActionsTodo().remove(eAction.SetWatchedState)) {
            procFile.ActionsDone().add(eAction.SetWatchedState);
            }*/
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.MLCmd_FileAdded, procFile.Id());

        } else if (replyId == 310) {
            //File Already Added

            if (configuration.overwriteMLEntries()) {
                procFile.ActionsTodo().add(eAction.MyListCmd);
                Cmd cmd = new Cmd(query.getCmd(), true);
                cmd.setArgs("edit", "1");
                api.queryCmd(cmd);
            }

            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.MLCmd_AlreadyAdded, procFile.Id());

        } else {
            procFile.ActionsError().add(eAction.MyListCmd);
            /*if (procFile.ActionsTodo().remove(eAction.SetWatchedState)) {
            procFile.ActionsError().add(eAction.SetWatchedState);
            }*/

            if (replyId == 320 || replyId == 330 || replyId == 350) {
                Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.MLCmd_NotFound, procFile.Id());
            } else {
                Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.MLCmd_Error, procFile.Id());
            }
        }

        if (!procFile.IsFinal() && !(procFile.ActionsTodo().contains(eAction.FileCmd) || (procFile.ActionsTodo().contains(eAction.MyListCmd)))) {
            finalProcessing(procFile);
        }
    }

    private void aniDBVoteReply(int queryId) {
        Query query = api.Queries().get(queryId);
        int replyId = query.getReply().ReplyId();

        int fileId = Integer.parseInt(query.getReply().Tag());
        if (!files.contains("Id", fileId)) {
            return; //File not found (Todo: throw error)
        }
        FileInfo procFile = files.get("Id", fileId);
        procFile.ActionsTodo().remove(eAction.MyListCmd);

        if (replyId == 260 && replyId == 262) {
            procFile.Data().put("Voted", "true"); //Voted
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.VoteCmd_EpVoted, procFile.Id());
        } else if (replyId == 263) {
            procFile.Data().put("Voted", "false");//Revoked
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.VoteCmd_EpVoteRevoked, procFile.Id());
        } else if (replyId == 363) {
            //PermVote Not Allowed
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.VoteCmd_Error, procFile.Id());
        }
    }

    private void finalProcessing(FileInfo procFile) {
        //System.out.println("Final processing");
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
    }

    private boolean renameFile(FileInfo procFile) { //Todo: refractor into smaller Methods
        String filename = "";
        try {
            TreeMap<String, String> ts = null;

            File folderObj = null;

            System.out.println("GUI_EnableFileMove" + configuration.enableFileMove());
            if (configuration.enableFileMove()) {
                if (configuration.moveTypeUseFolder()) {
                    folderObj = new File(configuration.moveToFolder());
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
            if (!configuration.enableFileRenaming()) {
                filename = procFile.FileObj().getName();
            } else if (configuration.renameTypeAniDBFileName()) {
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
            Log(CommunicationEvent.EventType.Information, eComType.FileEvent, "canWrite", renFile.canWrite());
            if (renFile.exists() && !(renFile.getParentFile().equals(procFile.FileObj().getParentFile()))) {
                Log(CommunicationEvent.EventType.Information, eComType.FileEvent, eComSubType.RenamingFailed, procFile.Id(), procFile.FileObj(), "Destination filename already exists.");
                if (configuration.deleteDuplicateFiles()) {
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
                    if (configuration.renameRelatedFiles()) {
                        // <editor-fold defaultstate="collapsed" desc="Rename Related Files">
                        try {

                            File srcFolder = procFile.FileObj().getParentFile();

                            File[] srcFiles = srcFolder.listFiles((dir, name) -> name.startsWith(oldFilenameWoExt)  && !name.equals(oldFilenameWoExt + ext));

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
        String command = "mv \"" + original.getAbsolutePath() + "\" \"" + targetFile.getAbsolutePath() + "\"";
        if (!targetFile.getParentFile().exists()) {
            appendToPostProcessingScript("mkdir -p " + targetFile.getParentFile().getAbsolutePath()); // Make sure the folder exists
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

        String codeStr = configuration.tagSystemCode();
        if (codeStr == null || codeStr.isEmpty()) {
            return null;
        }

        TagSystem.Evaluate(codeStr, tags);

        return tags;
    }

    public void addFiles(Collection<File> newFiles) {
        Boolean watched = configuration.setWatched() ? true : null;

        int storage = configuration.storageType().getValue();
        boolean rename = configuration.renameFiles();
        boolean addToMyList = configuration.addToMylist();

        for (File cf : newFiles) {
            if (files.contains("Path", cf.getAbsolutePath())) {
                continue;
            }

            FileInfo fileInfo = new FileInfo(cf, lastFileId);
            fileInfo.MLStorage(FileInfo.eMLStorageState.values()[storage]);
            fileInfo.ActionsTodo().add(eAction.Process);
            fileInfo.ActionsTodo().add(eAction.FileCmd);

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
        api = aniAdd.GetModule(Mod_UdpApi.class);

        api.registerEvent(this::aniDBInfoReply, "file");
        api.registerEvent(this::aniDBMyListReply, "mladd", "mldel");
        api.registerEvent(this::aniDBVoteReply, "vote");

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

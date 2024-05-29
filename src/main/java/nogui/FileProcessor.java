package nogui;

import aniAdd.AniAdd;
import aniAdd.Communication;
import aniAdd.IAniAdd;
import aniAdd.Modules.IModule;
import processing.Mod_EpProcessing;
import udpApi.Mod_UdpApi;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Created by Archie on 23.12.2015.
 */
public class FileProcessor implements IModule {

    private IAniAdd aniAdd;
    private Mod_EpProcessing epProc;
    private Mod_UdpApi api;

    private List<File> mFiles;


    public void start() {
        epProc.processing(Mod_EpProcessing.eProcess.Start);
    }


    // <editor-fold defaultstate="collapsed" desc="IModule">
    protected String modName = "FileProcessor";
    protected eModState modState = eModState.New;

    public eModState ModState() {
        return modState;
    }

    public String ModuleName() {
        return modName;
    }

    public void Initialize(IAniAdd aniAdd) {
        Logger.getGlobal().log(Level.WARNING, "INITIALIZE FileProcessor");

        modState = eModState.Initializing;
        this.aniAdd = aniAdd;
        epProc = (Mod_EpProcessing) aniAdd.GetModule("EpProcessing");
        api = (Mod_UdpApi) aniAdd.GetModule("UdpApi");

        String path = ((AniAdd) aniAdd).getDirectoryPath();
        File folder = new File(path);

        Logger.getGlobal().log(Level.WARNING, "Folder: " + folder.getAbsolutePath());
        File[] a = folder.listFiles();
        if (a != null) {
            mFiles = Arrays.stream(a).filter(File::isDirectory).flatMap(dir -> Arrays.stream(dir.listFiles())).collect(Collectors.toList());
            mFiles.addAll(Arrays.stream(a).filter(File::isFile).collect(Collectors.toList()));
            mFiles.forEach(f -> Logger.getGlobal().log(Level.WARNING, "Found file: " + f.getAbsolutePath()));
            Logger.getGlobal().log(Level.WARNING, "Number of found files: " + mFiles.size());
        } else {
            Logger.getGlobal().log(Level.WARNING, "Folder not found");
            System.exit(0);
        }

        epProc.addFiles(mFiles);
        epProc.processing(Mod_EpProcessing.eProcess.Start);

        epProc.addComListener(new ComListener() {
            @Override
            public void EventHandler(ComEvent comEvent) {
                if (comEvent.Type() == ComEvent.eType.Information) {
                    if (comEvent.ParamCount() == 3 &&
                            comEvent.Params(0) == Mod_EpProcessing.eComType.FileEvent &&
                            comEvent.Params(1) == Mod_EpProcessing.eComSubType.Done &&
                            comEvent.Params(2).equals(mFiles.size() - 1)) {
                        Logger.getGlobal().log(Level.WARNING, "File moving done, shutting down");
                        System.exit(0);
                    }
                }
            }
        });


        api.addComListener(new Communication.ComListener() {
            public void EventHandler(Communication.ComEvent comEvent) {
                if (comEvent.Type() == Communication.ComEvent.eType.Error || comEvent.Type() == Communication.ComEvent.eType.Fatal) {
                    //TODO: Terminate or retry or whatever
                }
//                if (comEvent.Type() == ComEvent.eType.Information )
            }
        });

        //TODO: What to do on these events?
//        epProc.addComListener(new Communication.ComListener() {
//            public void EventHandler(Communication.ComEvent comEvent) {
//                if (comEvent.Type() == Communication.ComEvent.eType.Information) {
//                    if (comEvent.Params(0) == Mod_EpProcessing.eComType.FileEvent) {
//                        int fileIndex = epProc.Id2Index((Integer) comEvent.Params(2));
//                        ((DefaultTableModel) tbl_Files.getModel()).fireTableRowsUpdated(fileIndex, fileIndex);
//                    } else if (comEvent.Params(0) == Mod_EpProcessing.eComType.FileCountChanged) {
//                        byteCount = epProc.totalBytes();
//                        //processedBytes = epProc.processedBytes();
//                        ((DefaultTableModel) tbl_Files.getModel()).fireTableDataChanged();
//                    } else if (comEvent.Params(0) == Mod_EpProcessing.eComType.Status) {
//                        EpProcStatusEvent(comEvent);
//                    }
//                } else if (comEvent.Type() == Communication.ComEvent.eType.Error || comEvent.Type() == Communication.ComEvent.eType.Fatal) {
//                    LockDown();
//                }
//
//            }
//        });
        modState = eModState.Initialized;
    }

    public void Terminate() {
        modState = eModState.Terminating;

        modState = eModState.Terminated;
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Com System">
    private ArrayList<ComListener> listeners = new ArrayList<ComListener>();

    protected void ComFire(ComEvent comEvent) {
        for (ComListener listener : listeners) {
            listener.EventHandler(comEvent);
        }
    }

    public void addComListener(ComListener comListener) {
        listeners.add(comListener);
    }

    public void RemoveComListener(ComListener comListener) {
        listeners.remove(comListener);
    }

    protected void Log(ComEvent.eType type, Object... params) {
        ComFire(new ComEvent(this, type, params));
    }

    // </editor-fold>
}

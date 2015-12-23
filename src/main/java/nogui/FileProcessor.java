package nogui;

import aniAdd.Communication;
import aniAdd.IAniAdd;
import aniAdd.Modules.IModule;
import processing.Mod_EpProcessing;
import udpApi.Mod_UdpApi;

import java.util.ArrayList;

/**
 * Created by Archie on 23.12.2015.
 */
public class FileProcessor implements IModule {

    private IAniAdd aniAdd;
    private Mod_EpProcessing epProc;
    private Mod_UdpApi api;


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
        modState = eModState.Initializing;
        this.aniAdd = aniAdd;
        epProc = (Mod_EpProcessing) aniAdd.GetModule("EpProcessing");
        api = (Mod_UdpApi) aniAdd.GetModule("UdpApi");

        api.addComListener(new Communication.ComListener() {
            public void EventHandler(Communication.ComEvent comEvent) {
                if (comEvent.Type() == Communication.ComEvent.eType.Error || comEvent.Type() == Communication.ComEvent.eType.Fatal) {
                    //TODO: Terminate or retry or whatever
                }
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

    class AniAddEventHandler implements ComListener {

        public void EventHandler(ComEvent comEvent) {
        }
    }
    // </editor-fold>
}

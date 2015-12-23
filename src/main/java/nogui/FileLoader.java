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
public class FileLoader implements IModule {


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

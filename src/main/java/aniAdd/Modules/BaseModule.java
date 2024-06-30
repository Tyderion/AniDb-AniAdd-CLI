package aniAdd.Modules;

import java.util.ArrayList;

public abstract class BaseModule implements IModule {
    private final ArrayList<ComListener> listeners = new ArrayList<>();

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

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Misc">
    protected void Log(ComEvent.eType type, Object... params) {
        ComFire(new ComEvent(this, type, params));
    }
}

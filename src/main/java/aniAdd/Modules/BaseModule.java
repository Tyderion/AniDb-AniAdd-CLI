package aniAdd.Modules;

import java.util.ArrayList;

public abstract class BaseModule implements IModule {
    private final ArrayList<ComListener> listeners = new ArrayList<>();

    protected void ComFire(CommunicationEvent communicationEvent) {
        for (ComListener listener : listeners) {
            listener.handleEvent(communicationEvent);
        }

    }

    public void addComListener(ComListener comListener) {
        listeners.add(comListener);
    }

    protected void Log(CommunicationEvent.EventType eventType, Object... params) {
        ComFire(new CommunicationEvent(this, eventType, params));
    }
}

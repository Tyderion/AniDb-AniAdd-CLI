package aniAdd;

import aniAdd.Modules.IModule;
import aniAdd.config.AniConfiguration;

import java.util.*;

import nogui.FileProcessor;
import processing.Mod_AnimeProcessing;
import processing.Mod_EpProcessing;
import udpApi.Mod_UdpApi;

/**
 * @author Arokh
 */
public class AniAdd implements IAniAdd {
    final static int CURRENTVER = 9;
    private final AniConfiguration mConfiguration;

    private final Map<Class<? extends IModule>, IModule> modules = new HashMap<>();
    private final EventHandler eventHandler = new EventHandler();

    public String getDirectoryPath() {
        return mConfiguration.directory();
    }

    public AniAdd(AniConfiguration configuration) {
        mConfiguration = configuration;
        addModule(new Mod_EpProcessing(mConfiguration));
        addModule(new Mod_AnimeProcessing());
        addModule(new Mod_UdpApi());

        addModule(new FileProcessor());
    }

    private void addModule(IModule mod) {
        modules.put(mod.getClass(), mod);
        eventHandler.AddEventHandler(mod);
    }

    public void Start() {
        ComFire(new CommunicationEvent(this, CommunicationEvent.EventType.Information, IModule.eModState.Initializing));

        for (IModule module : modules.values()) {
            System.out.println("Initializing: " + module.ModuleName());
            module.Initialize(this);
        }

        boolean allModsInitialized = false;
        while (!allModsInitialized) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            allModsInitialized = true;
            for (IModule module : modules.values()) {
                allModsInitialized &= module.ModState() == IModule.eModState.Initialized;
            }
        }
        ComFire(new CommunicationEvent(this, CommunicationEvent.EventType.Information, IModule.eModState.Initialized));
    }

    public void Stop() {
        ComFire(new CommunicationEvent(this, CommunicationEvent.EventType.Information, IModule.eModState.Terminating));

        for (IModule module : modules.values()) {
            System.out.println("Terminating: " + module.ModuleName());
            module.Terminate();
        }

        boolean allModsTerminated = false;
        while (!allModsTerminated) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
            }

            allModsTerminated = true;
            for (IModule module : modules.values()) {
                allModsTerminated &= module.ModState() == IModule.eModState.Terminated;
            }
        }


        ComFire(new CommunicationEvent(this, CommunicationEvent.EventType.Information, IModule.eModState.Terminated));
    }

    @SuppressWarnings("unchecked")
    public <T extends IModule> T GetModule(Class<T> modName) {
        return (T) modules.get(modName);
    }

    static class EventHandler implements ComListener {
        public void AddEventHandler(IModule mod) {
            mod.addComListener(this);
        }

        public void handleEvent(CommunicationEvent communicationEvent) {
            System.out.println("Event: " + communicationEvent.toString());
        }
    }


    //Com System
    private final ArrayList<ComListener> listeners = new ArrayList<ComListener>();

    protected void ComFire(CommunicationEvent communicationEvent) {
        System.out.println("AniAdd Event: " + communicationEvent.toString());
        for (ComListener listener : listeners) listener.handleEvent(communicationEvent);

    }

    public void addComListener(ComListener comListener) {
        listeners.add(comListener);
    }

    //Com System End
}

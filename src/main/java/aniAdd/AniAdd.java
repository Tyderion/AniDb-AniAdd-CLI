package aniAdd;

import aniAdd.Modules.IModule;
import aniAdd.config.AniConfiguration;
import aniAdd.misc.Mod_Memory;

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

    Map<Class<? extends IModule>, IModule> modules = new HashMap<>();
    EventHandler eventHandler;
    Mod_Memory mem;

    public AniAdd() {
        this(null);
    }

    public String getDirectoryPath() {
        return mConfiguration.getDirectory();
    }

    public AniAdd(AniConfiguration configuration) {
        mConfiguration = configuration;
        eventHandler = new EventHandler();

        mem = mConfiguration == null ? new Mod_Memory() : new Mod_Memory(mConfiguration);
        addModule(mem);
        addModule(new Mod_EpProcessing());
        addModule(new Mod_AnimeProcessing());
        addModule(new Mod_UdpApi());

        addModule(new FileProcessor());
    }

    private void addModule(IModule mod) {
        modules.put(mod.getClass(), mod);
        eventHandler.AddEventHandler(mod);
    }

    public void Start() {
        ComFire(new ComEvent(this, ComEvent.eType.Information, IModule.eModState.Initializing));

        for (IModule module : modules.values()) {
            System.out.println("Initializing: " + module.ModuleName());
            module.Initialize(this);
        }

        boolean allModsInitialized = false;
        while (!allModsInitialized) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
            }

            allModsInitialized = true;
            for (IModule module : modules.values()) {
                allModsInitialized &= module.ModState() == IModule.eModState.Initialized;
            }
        }

        mem.put("FirstStart", CURRENTVER);
        ComFire(new ComEvent(this, ComEvent.eType.Information, IModule.eModState.Initialized));
    }

    public void Stop() {
        ComFire(new ComEvent(this, ComEvent.eType.Information, IModule.eModState.Terminating));

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


        ComFire(new ComEvent(this, ComEvent.eType.Information, IModule.eModState.Terminated));
    }

    public <T extends IModule> T GetModule(Class<T> modName) {
        return (T) modules.get(modName.getClass());
    }

    public Collection<IModule> GetModules() {
        return modules.values();
    }

    class EventHandler implements ComListener {
        public void AddEventHandler(IModule mod) {
            mod.addComListener(this);
        }

        public void EventHandler(ComEvent comEvent) {
            System.out.println("Event: " + comEvent.toString());
        }
    }


    //Com System
    private ArrayList<ComListener> listeners = new ArrayList<ComListener>();

    protected void ComFire(ComEvent comEvent) {
        System.out.println("AniAdd Event: " + comEvent.toString());
        for (ComListener listener : listeners) listener.EventHandler(comEvent);

    }

    public void addComListener(ComListener comListener) {
        listeners.add(comListener);
    }

    public void RemoveComListener(ComListener comListener) {
        listeners.remove(comListener);
    }
    //Com System End
}

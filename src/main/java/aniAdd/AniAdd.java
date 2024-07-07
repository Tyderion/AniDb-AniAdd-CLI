package aniAdd;

import aniAdd.Modules.IModule;
import aniAdd.config.AniConfiguration;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import aniAdd.misc.ICallBack;
import lombok.val;
import processing.FileProcessor;
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
    private boolean allInitialized = false;
    private boolean exitOnTermination = false;

    public AniAdd(AniConfiguration configuration) {
        mConfiguration = configuration;
        addModule(new Mod_EpProcessing(mConfiguration));
        addModule(new Mod_UdpApi());

        addModule(new FileProcessor());
    }

    @Override
    public void ProcessDirectory(String directory) {
        runCommand(_ -> {
            FileProcessor fileProcessor = GetModule(FileProcessor.class);
            fileProcessor.Scan(directory);
        });
    }

    @Override
    public void MarkFileAsWatched(String path) {
        val config = getConfiguration().toBuilder()
                .addToMylist(true)
                .renameFiles(false)
                .enableFileMove(false)
                .enableFileRenaming(false)
                .setWatched(true)
                .overwriteMLEntries(true)
                .build();
        runCommand(_ -> {
            val fileProcessor = GetModule(FileProcessor.class);
            fileProcessor.AddFile(path, config);
        });
    }

    private void runCommand(ICallBack<Void> callback) {
        if (allInitialized) {
            callback.invoke(null);
        } else {
            addComListener(comEvent -> {
                if (comEvent.EventType() == Communication.CommunicationEvent.EventType.Information) {
                    if (comEvent.Params(0) == IModule.eModState.Initialized) {
                        callback.invoke(null);
                    }
                }
            });
        }
    }

    @Override
    public AniConfiguration getConfiguration() {
        return mConfiguration;
    }

    private void addModule(IModule mod) {
        modules.put(mod.getClass(), mod);
        eventHandler.AddEventHandler(mod);
    }

    public void Start(boolean exitOnTermination) {
        this.exitOnTermination = exitOnTermination;
        ComFire(new CommunicationEvent(this, CommunicationEvent.EventType.Information, IModule.eModState.Initializing));

        for (IModule module : modules.values()) {
            System.out.println("Initializing: " + module.ModuleName());
            module.Initialize(this, mConfiguration);
        }

        while (!allInitialized) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            allInitialized = true;
            for (IModule module : modules.values()) {
                allInitialized &= module.ModState() == IModule.eModState.Initialized;
            }
        }
        if (exitOnTermination) {
            addComListener(comEvent -> {
                if (comEvent.EventType() == CommunicationEvent.EventType.Information) {
                    if (comEvent.ParamCount() == 3 &&
                            comEvent.Params(0) == Mod_EpProcessing.eComType.FileEvent &&
                            comEvent.Params(1) == Mod_EpProcessing.eComSubType.Done &&
                            comEvent.Params(2).equals(0)) {
                        Logger.getGlobal().log(Level.INFO, "File moving done, shutting down");
                        Stop();
                    }
                }
            });
            addComListener(communicationEvent -> {
                if (communicationEvent.EventType() == CommunicationEvent.EventType.Information
                        && communicationEvent.Params(0) == IModule.eModState.Terminated) {
                    System.exit(0);
                }
            });
        }
        ComFire(new CommunicationEvent(this, CommunicationEvent.EventType.Information, IModule.eModState.Initialized));
    }

    public void Stop() {
        ComFire(new CommunicationEvent(this, CommunicationEvent.EventType.Information, IModule.eModState.Terminating));

        for (IModule module : modules.values()) {
            System.out.println(STR."Terminating: \{module.ModuleName()}");
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
        allInitialized = false;


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

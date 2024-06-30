package nogui;

import aniAdd.AniAdd;
import aniAdd.IAniAdd;
import aniAdd.Modules.BaseModule;
import processing.Mod_EpProcessing;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Created by Archie on 23.12.2015.
 */
public class FileProcessor extends BaseModule {

    private Mod_EpProcessing epProc;

    private List<File> mFiles;


    public void start() {
        epProc.processing(Mod_EpProcessing.eProcess.Start);
    }

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
        epProc = aniAdd.GetModule(Mod_EpProcessing.class);

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

        epProc.addComListener(comEvent -> {
            if (comEvent.EventType() == CommunicationEvent.EventType.Information) {
                if (comEvent.ParamCount() == 3 &&
                        comEvent.Params(0) == Mod_EpProcessing.eComType.FileEvent &&
                        comEvent.Params(1) == Mod_EpProcessing.eComSubType.Done &&
                        comEvent.Params(2).equals(mFiles.size() - 1)) {
                    Logger.getGlobal().log(Level.WARNING, "File moving done, shutting down");
                    System.exit(0);
                }
            }
        });
        modState = eModState.Initialized;
    }

    public void Terminate() {
        modState = eModState.Terminated;
    }

}

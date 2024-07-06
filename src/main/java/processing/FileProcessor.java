package processing;

import aniAdd.IAniAdd;
import aniAdd.Modules.BaseModule;
import aniAdd.config.AniConfiguration;

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
    private IAniAdd aniAdd;

    public void start() {
        epProc.processing(Mod_EpProcessing.eProcess.Start);
    }

    protected String modName = "FileProcessor";
    protected eModState modState = eModState.New;

    private boolean ready = false;

    public eModState ModState() {
        return modState;
    }

    public String ModuleName() {
        return modName;
    }

    public void Initialize(IAniAdd aniAdd, AniConfiguration configuration) {
        this.aniAdd = aniAdd;
        Logger.getGlobal().log(Level.WARNING, "INITIALIZE FileProcessor");

        modState = eModState.Initializing;
        epProc = aniAdd.GetModule(Mod_EpProcessing.class);

        aniAdd.addComListener(communicationEvent -> {
            if (communicationEvent.EventType() == CommunicationEvent.EventType.Information
                    && communicationEvent.ParamCount() == 1
                    && communicationEvent.Params(0) == eModState.Initialized) {
               ready = true;
            }
        });

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

    public void Scan(String directory) {
        File folder = new File(directory);

        Logger.getGlobal().log(Level.WARNING, "Folder: " + folder.getAbsolutePath());
        File[] a = folder.listFiles(this::shouldScrapeFile);
        if (a != null) {
            mFiles = Arrays.stream(a).filter(File::isDirectory).flatMap(dir -> Arrays.stream(dir.listFiles())).collect(Collectors.toList());
            mFiles.addAll(Arrays.stream(a).filter(File::isFile).collect(Collectors.toList()));
            mFiles.forEach(f -> Logger.getGlobal().log(Level.WARNING, "Found file: " + f.getAbsolutePath()));
            Logger.getGlobal().log(Level.WARNING, "Number of found files: " + mFiles.size());
        } else {
            Logger.getGlobal().log(Level.WARNING, "Folder not found");
            System.exit(0);
        }

        if (mFiles.isEmpty()) {
            Logger.getGlobal().log(Level.WARNING, "No files found, shutting down");
            System.exit(0);
        }

        epProc.addFiles(mFiles);
        if (ready) {
            start();
        } else {
            aniAdd.addComListener(communicationEvent -> {
                if (communicationEvent.EventType() == CommunicationEvent.EventType.Information
                        && communicationEvent.ParamCount() == 1
                        && communicationEvent.Params(0) == eModState.Initialized) {
                    start();
                }
            });
        }
    }

    public void Terminate() {
        modState = eModState.Terminated;
    }

    private boolean shouldScrapeFile(File _directory, String name) {
        return !isKodiMetadataFileOrInvalidFile(_directory, name) && !(name.contains("- S01E") || name.contains("- S00E"));
    }

    private boolean isKodiMetadataFileOrInvalidFile(File _directory, String name) {
        return name.endsWith(".jpg")
                || name.endsWith(".nfo")
                || name.endsWith(".srt")
                || name.endsWith(".sub")
                || name.endsWith(".png")
                || name.equalsIgnoreCase("thumbs.db");
    }

}

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
        Logger.getGlobal().log(Level.INFO, "INITIALIZE FileProcessor");

        modState = eModState.Initializing;
        epProc = aniAdd.GetModule(Mod_EpProcessing.class);

        aniAdd.addComListener(communicationEvent -> {
            if (communicationEvent.EventType() == CommunicationEvent.EventType.Information
                    && communicationEvent.ParamCount() == 1
                    && communicationEvent.Params(0) == eModState.Initialized) {
                ready = true;
            }
        });
        modState = eModState.Initialized;
    }

    public void AddFile(String path) {
        AddFile(path, aniAdd.getConfiguration());
    }

    public void AddFile(String path, AniConfiguration configuration) {
        File file = new File(path);
        if (file.exists()) {
            epProc.addFiles(List.of(file), configuration);
            startFileProcessing();
            epProc.addComListener(comEvent -> {
                if (comEvent.EventType() == CommunicationEvent.EventType.Information) {
                    if (comEvent.ParamCount() == 3 &&
                            comEvent.Params(0) == Mod_EpProcessing.eComType.FileEvent &&
                            comEvent.Params(1) == Mod_EpProcessing.eComSubType.Done &&
                            comEvent.Params(2).equals(0)) {
                        Logger.getGlobal().log(Level.INFO, "File moving done, shutting down");
                        aniAdd.Stop();
                    }
                }
            });
        }
    }

    public void Scan(String directory) {
        File folder = new File(directory);

        Logger.getGlobal().log(Level.WARNING, STR."Folder: \{folder.getAbsolutePath()}");
        File[] a = folder.listFiles(this::shouldScrapeFile);

        if (a != null) {
            final List<File> files = Arrays.stream(a).filter(File::isDirectory).flatMap(dir -> Arrays.stream(dir.listFiles(this::shouldScrapeFile))).collect(Collectors.toList());
            files.addAll(Arrays.stream(a).filter(File::isFile).collect(Collectors.toList()));
            files.forEach(f -> Logger.getGlobal().log(Level.INFO, STR."Found file: \{f.getAbsolutePath()}"));
            Logger.getGlobal().log(Level.INFO, STR."Number of found files: \{files.size()}");
            if (files.isEmpty()) {
                Logger.getGlobal().log(Level.WARNING, "No files found, shutting down");
                aniAdd.Stop();
            }

            epProc.addFiles(files);
            epProc.addComListener(comEvent -> {
                if (comEvent.EventType() == CommunicationEvent.EventType.Information) {
                    if (comEvent.ParamCount() == 3 &&
                            comEvent.Params(0) == Mod_EpProcessing.eComType.FileEvent &&
                            comEvent.Params(1) == Mod_EpProcessing.eComSubType.Done &&
                            comEvent.Params(2).equals(files.size() - 1)) {
                        Logger.getGlobal().log(Level.INFO, "File moving done, shutting down");
                        aniAdd.Stop();
                    }
                }
            });

            startFileProcessing();
        } else {
            Logger.getGlobal().log(Level.WARNING, STR."Folder not found: \{folder.getAbsolutePath()}");
            aniAdd.Stop();
        }

    }

    private void startFileProcessing() {
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
        ready = false;
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

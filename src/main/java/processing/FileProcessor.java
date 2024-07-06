package processing;

import aniAdd.IAniAdd;
import aniAdd.Modules.BaseModule;
import aniAdd.config.AniConfiguration;
import lombok.val;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        }
    }

    private Stream<File> recursivelyGetFilesToScrape(File folder) {
        if (!folder.exists() || !folder.isDirectory()) {
            return Arrays.stream(new File[0]);
        }
        val files = Arrays.stream(folder.listFiles(this::shouldScrapeFile));
        val subFolderFiles = Arrays.stream(folder.listFiles(File::isDirectory)).flatMap(this::recursivelyGetFilesToScrape);
        return Stream.concat(files, subFolderFiles);
    }

    public void Scan(String directory) {
        File folder = new File(directory);

        Logger.getGlobal().log(Level.WARNING, STR."Folder: \{folder.getAbsolutePath()}");

        val files = recursivelyGetFilesToScrape(folder).collect(Collectors.toList());
        files.forEach(f -> Logger.getGlobal().log(Level.INFO, STR."Found file: \{f.getAbsolutePath()}"));
        Logger.getGlobal().log(Level.INFO, STR."Number of found files: \{files.size()}");
        if (files.isEmpty()) {
            Logger.getGlobal().log(Level.WARNING, "No files found, shutting down");
        } else {
            epProc.addFiles(files);
            startFileProcessing();
        }
    }

    private void startFileProcessing() {
        start();
    }

    public void Terminate() {
        ready = false;
        modState = eModState.Terminated;
    }

    private boolean shouldScrapeFile(File _directory, String name) {
        return !isKodiMetadataFileOrInvalidFile(_directory, name);
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

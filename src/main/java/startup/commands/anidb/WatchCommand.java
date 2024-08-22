package startup.commands.anidb;

import cache.PersistenceConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import startup.commands.util.CommandHelper;
import startup.validation.validators.min.Min;
import startup.validation.validators.nonblank.NonBlank;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@CommandLine.Command(name = "watch", mixinStandardHelpOptions = true, version = "1.0",
        description = "Periodically scans the directory for files and adds them to AniDb")
public class WatchCommand extends KodiWatcherCommand {
    @Min(value = 10, message = "Interval must be at least 10 minutes")
    @CommandLine.Option(names = {"-i", "--interval"}, description = "The interval in minutes to scan the directory", defaultValue = "30")
    private int interval;

    @CommandLine.Option(names = {"--kodi"}, description = "The interval in minutes to scan the directory", defaultValue = "false")
    private boolean kodi;

    @NonBlank
    @CommandLine.Parameters(index = "0", description = "The directory to scan.")
    private Path directory;

    @CommandLine.ParentCommand
    private AnidbCommand parent;

    @Override
    public Integer call() throws Exception {
        log.info(STR."Watching directory \{directory} every \{interval} minutes");
        try (val executorService = Executors.newScheduledThreadPool(10);
             val sessionFactory = PersistenceConfiguration.getSessionFactory(parent.getDbPath());
             val filesystem = new DoOnFileSystem()) {
            val aniAddO = parent.initializeAniAdd(false, executorService, filesystem, directory, sessionFactory);
            if (aniAddO.isEmpty()) {
                executorService.shutdownNow();
                return 1;
            }

            val aniAdd = aniAddO.get();
            executorService.scheduleAtFixedRate(() -> aniAdd.ProcessDirectory(directory), 0, interval, TimeUnit.MINUTES);

            if (kodi) {
                this.startKodiListener(parent.getConfiguration().kodi(), aniAdd);
            }

            val _ = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        return 0;
    }

    public static String getName() {
        return CommandHelper.getName(WatchCommand.class);
    }
}

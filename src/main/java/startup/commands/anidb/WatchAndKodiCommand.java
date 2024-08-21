package startup.commands.anidb;

import aniAdd.kodi.KodiNotificationSubscriber;
import cache.PersistenceConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import startup.commands.util.CommandHelper;
import startup.validation.validators.config.OverrideConfig;
import startup.validation.validators.min.Min;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.port.Port;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@CommandLine.Command(name = "watch-and-kodi", mixinStandardHelpOptions = true, version = "1.0",
        description = "Watches a folder to scan and also connects to a kodi instance via websockets and marks watched episodes as watched on anidb as well. Filepath must contain 'anime' (configurable)")
public class WatchAndKodiCommand implements Callable<Integer> {
    @OverrideConfig(configPath = "kodi.port", envVariableName = "KODI_PORT", required = true)
    @CommandLine.Option(names = {"--port"}, description = "The port to connect to")
    @Port private int port;

    @OverrideConfig(configPath = "kodi.host", envVariableName = "KODI_HOST", required = true)
    @CommandLine.Option(names = {"--kodi"}, description = "The ip/hostname of the kodi server.")
    @NonBlank private String kodiUrl;

    @OverrideConfig(configPath = "kodi.pathFilter")
    @CommandLine.Option(names = {"--path-filter"}, description = "The path filter to use to detect anime files. Default is 'anime'. Case insensitive.", defaultValue = "anime")
    private String pathFilter;

    @NonBlank
    @CommandLine.Parameters(index = "0", description = "The directory to scan.")
    private Path directory;

    @Min(value = 10, message = "Interval must be at least 10 minutes")
    @CommandLine.Option(names = {"-i", "--interval"}, description = "The interval in minutes to scan the directory", defaultValue = "30")
    private int interval;

    @CommandLine.ParentCommand
    private AnidbCommand parent;


    @Override
    public Integer call() throws Exception {
        log.info(STR."Connecting to kodi at \{kodiUrl} on port \{port}");

        try (val executorService = Executors.newScheduledThreadPool(10);
             val sessionFactory = PersistenceConfiguration.getSessionFactory(parent.getDbPath());
             val filesystem = new DoOnFileSystem()) {
            val configuration = parent.getConfiguration();
            if (kodiUrl == null && (configuration.kodi().host() == null || configuration.kodi().host().isBlank())) {
                log.error("No kodi host provided. Please provide a host in the config file or via the command line.");
                return 1;
            }
            val aniAddO = parent.initializeAniAdd(false, executorService, filesystem, directory, sessionFactory);
            if (aniAddO.isEmpty()) {
                executorService.shutdownNow();
                return 1;
            }

            val aniAdd = aniAddO.get();
            val subscriber = new KodiNotificationSubscriber(new URI(STR."ws://\{kodiUrl}:\{port}/jsonrpc"), aniAdd, pathFilter);
            subscriber.connect();
            executorService.scheduleAtFixedRate(() -> aniAdd.ProcessDirectory(directory), 0, interval, TimeUnit.MINUTES);

            val _ = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }


        return 0;
    }

    public static List<String> getOptions() {
        return CommandHelper.getOptions(WatchAndKodiCommand.class);
    }

    public static String getName() {
        return CommandHelper.getName(WatchAndKodiCommand.class);
    }
}

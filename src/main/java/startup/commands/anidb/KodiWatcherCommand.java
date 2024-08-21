package startup.commands.anidb;

import aniAdd.kodi.KodiNotificationSubscriber;
import cache.PersistenceConfiguration;
import config.CliConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import startup.commands.util.CommandHelper;
import startup.validation.validators.config.OverridesConfig;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.port.Port;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@CommandLine.Command(name = "connect-to-kodi", mixinStandardHelpOptions = true, version = "1.0",
        description = "Connects to a kodi instance via websockets and marks watched episodes as watched on anidb as well. Filepath must contain 'anime' (configurable)")
public class KodiWatcherCommand implements Callable<Integer> {
    @Port
    @OverridesConfig(configPath = "kodi.port", envVariableName = "KODI_PORT", required = true)
    @CommandLine.Option(names = {"--port"}, description = "The port to connect to")
    private Integer port;

    @NonBlank(allowNull = true)
    @OverridesConfig(configPath = "kodi.host", envVariableName = "KODI_HOST", required = true)
    @CommandLine.Option(names = {"--kodi"}, description = "The ip/hostname of the kodi server.")
    private String kodiUrl;

    @CommandLine.Option(names = {"--path-filter"}, description = "The path filter to use to detect anime files. Default is 'anime'. Case insensitive.")
    private String pathFilter;

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
            val aniAddO = parent.initializeAniAdd(false, executorService, filesystem, null, sessionFactory);
            if (aniAddO.isEmpty()) {
                executorService.shutdownNow();
                return 1;
            }

            CliConfiguration config = parent.getConfiguration();
            if ((kodiUrl == null || kodiUrl.isBlank()) && (config.kodi() == null || config.kodi().host() == null || config.kodi().host().isBlank())) {
                log.error("No kodi host found in the configuration or the command arguments. Exiting.");
                return 1;
            }

            val kodiUrl = this.kodiUrl == null ? parent.getConfiguration().kodi().host() : this.kodiUrl;
            val kodiPort = this.port == null ? parent.getConfiguration().kodi().port() : this.port;

            val aniAdd = aniAddO.get();
            val subscriber = new KodiNotificationSubscriber(new URI(STR."ws://\{kodiUrl}:\{kodiPort}/jsonrpc"), aniAdd, pathFilter);
            subscriber.connect();

            val _ = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }


        return 0;
    }

    public static List<String> getOptions() {
        return CommandHelper.getOptions(KodiWatcherCommand.class);
    }

    public static String getName() {
        return CommandHelper.getName(KodiWatcherCommand.class);
    }
}

package startup.commands.anidb;

import aniAdd.IAniAdd;
import aniAdd.kodi.KodiNotificationSubscriber;
import cache.PersistenceConfiguration;
import config.blocks.KodiConfig;
import config.blocks.PathConfig;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import startup.commands.util.CommandHelper;
import startup.validation.validators.config.MapConfig;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.port.Port;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@CommandLine.Command(name = "connect-to-kodi", mixinStandardHelpOptions = true, version = "1.0",
        description = "Connects to a kodi instance via websockets and marks watched episodes as watched on anidb as well. Filepath must contain 'anime' (configurable)")
public class KodiWatcherCommand implements Callable<Integer> {
    @Port
    @MapConfig(configPath = "kodi.port", envVariableName = "KODI_PORT", required = true)
    @CommandLine.Option(names = {"--kodi-port"}, description = "The port to connect to")
    private Integer port;

    @NonBlank(allowNull = true)
    @MapConfig(configPath = "kodi.host", envVariableName = "KODI_HOST", required = true)
    @CommandLine.Option(names = {"--kodi-host"}, description = "The ip/hostname of the kodi server.")
    private String kodiUrl;

    @MapConfig(configPath = "kodi.pathFilter")
    @CommandLine.Option(names = {"--path-filter"}, description = "The path filter to use to detect anime files. Default is 'anime'. Case insensitive.")
    private String pathFilter;

    @CommandLine.ParentCommand
    private AnidbCommand parent;

    @MapConfig(configPath = "kodi", required = true)
    private KodiConfig kodiConfig;

    @MapConfig(configPath = "tags.path", required = true)
    private PathConfig pathsConfig;

    @Override
    public Integer call() throws Exception {
        log.info(STR."Connecting to kodi at \{kodiUrl} on port \{port}");
        try (val executorService = Executors.newScheduledThreadPool(10);
             val sessionFactory = PersistenceConfiguration.getSessionFactory(parent.getDbPath());
             val filesystem = new DoOnFileSystem()) {
            val aniAddO = parent.initializeAniAdd(false, executorService, filesystem, null, sessionFactory);
            if (aniAddO.isEmpty()) {
                executorService.shutdownNow();
                return 1;
            }
            startKodiListener(kodiConfig, aniAddO.get());

            val _ = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        return 0;
    }

    protected void startKodiListener(KodiConfig kodiConfig, IAniAdd aniAdd) throws URISyntaxException {
        val subscriber = new KodiNotificationSubscriber(
                new URI(STR."ws://\{kodiConfig.host()}:\{kodiConfig.port()}/jsonrpc"),
                aniAdd, pathsConfig, kodiConfig);
        subscriber.connect();
    }

    public static String getName() {
        return CommandHelper.getName(KodiWatcherCommand.class);
    }
}

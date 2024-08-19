package startup.commands.anidb;

import aniAdd.kodi.KodiNotificationSubscriber;
import cache.PersistenceConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.port.Port;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@CommandLine.Command(name = "connect-to-kodi", mixinStandardHelpOptions = true, version = "1.0",
        description = "Connects to a kodi instance via websockets and marks watched episodes as watched on anidb as well. Filepath must contain 'anime' (configurable)")
public class KodiWatcherCommand implements Callable<Integer> {
    @CommandLine.Option(names = {"--port"}, description = "The port to connect to", defaultValue = "9090")
    @Port private int port;

    @CommandLine.Option(names = {"--kodi"}, description = "The ip/hostname of the kodi server.", required = true, defaultValue = "localhost")
    @NonBlank private String kodiUrl;

    @CommandLine.Option(names = {"--path-filter"}, description = "The path filter to use to detect anime files. Default is 'anime'. Case insensitive.", defaultValue = "anime")
    private String pathFilter;

    @CommandLine.ParentCommand
    private AnidbCommand parent;


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

            val aniAdd = aniAddO.get();
            val subscriber = new KodiNotificationSubscriber(new URI(STR."ws://\{kodiUrl}:\{port}/jsonrpc"), aniAdd, pathFilter);
            subscriber.connect();

            val _ = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }


        return 0;
    }
}

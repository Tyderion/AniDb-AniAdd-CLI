package aniAdd.startup.commands.anidb;

import aniAdd.kodi.KodiNotificationSubscriber;
import aniAdd.startup.validation.validators.min.Min;
import aniAdd.startup.validation.validators.nonempty.NonEmpty;
import aniAdd.startup.validation.validators.port.Port;
import lombok.extern.java.Log;
import lombok.val;
import picocli.CommandLine;

import java.net.URI;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Log
@CommandLine.Command(name = "watch-and-kodi", mixinStandardHelpOptions = true, version = "1.0",
        description = "Watches a folder to scan and also connects to a kodi instance via websockets and marks watched episodes as watched on anidb as well. Filepath must contain 'anime' (configurable)")
public class WatchAndKodiCommand implements Callable<Integer> {
    @CommandLine.Option(names = {"--port"}, description = "The port to connect to")
    @Port private int port = 9090;

    @CommandLine.Option(names = {"--kodi"}, description = "The ip/hostname of the kodi server.", required = true)
    @NonEmpty private String kodiUrl = "localhost";

    @CommandLine.Option(names = {"--path-filter"}, description = "The path filter to use to detect anime files. Default is 'anime'. Case insensitive.", defaultValue = "anime")
    private String pathFilter;

    @CommandLine.Parameters(index = "0", description = "The directory to scan.")
    @NonEmpty
    private String directory;

    @Min(value = 10, message = "Interval must be at least 10 minutes")
    @CommandLine.Option(names = {"-i", "--interval"}, description = "The interval in minutes to scan the directory", defaultValue = "30")
    private int interval;

    @CommandLine.ParentCommand
    private AnidbCommand parent;


    @Override
    public Integer call() throws Exception {
        log.info(STR."Connecting to kodi at \{kodiUrl} on port \{port}");

        try (val executorService = Executors.newScheduledThreadPool(10)) {
            val aniAddO = parent.initializeAniAdd(false, executorService, directory);
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
}

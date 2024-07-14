package aniAdd.startup.commands.anidb;

import aniAdd.kodi.KodiNotificationSubscriber;
import lombok.val;
import picocli.CommandLine;

import java.net.URI;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@CommandLine.Command(name = "connect-to-kodi", mixinStandardHelpOptions = true, version = "1.0",
        description = "Connects to a kodi instance via websockets and marks watched episodes as watched on anidb as well. Filepath must contain 'anime'.")
public class ServerCommand implements Callable<Integer> {
    @CommandLine.Option(names = {"--port"}, description = "The port to connect to")
    private int port = 9090;
    @CommandLine.Option(names = {"--kodi"}, description = "The ip/hostname of the kodi server.", required = true)
    private String kodiUrl = "localhost";

    @CommandLine.ParentCommand
    private AnidbCommand parent;


    @Override
    public Integer call() throws Exception {
        Logger.getGlobal().info((STR."Connecting to kodi at \{kodiUrl} on port \{port}"));

        try (val executorService = Executors.newScheduledThreadPool(10)) {
            val aniAdd = parent.initializeAniAdd(false, executorService);
            val subscriber = new KodiNotificationSubscriber(new URI(STR."ws://\{kodiUrl}:\{port}/jsonrpc"), aniAdd);
            subscriber.connect();

            val _ = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }


        return 0;
    }
}

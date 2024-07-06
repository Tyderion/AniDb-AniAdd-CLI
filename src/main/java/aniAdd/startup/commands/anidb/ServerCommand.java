package aniAdd.startup.commands.anidb;

import aniAdd.kodi.KodiNotificationSubscriber;
import lombok.val;
import picocli.CommandLine;
import java.net.URI;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "connect-to-kodi", mixinStandardHelpOptions = true, version = "1.0",
        description = "Starts the server.")
public class ServerCommand implements Callable<Integer> {
    @CommandLine.Option(names = {"--port"}, description = "The port to listen on.")
    private int port = 9090;
    @CommandLine.Option(names = {"--kodiUrl"}, description = "The url of the kodi server.")
    private String kodiUrl = "localhost";

    @CommandLine.ParentCommand
    private AnidbCommand parent;


    @Override
    public Integer call() throws Exception {
        System.out.println(STR."Connecting to kodi at \{kodiUrl} on port \{port}");

        val subscriber = new KodiNotificationSubscriber(new URI(STR."ws://\{kodiUrl}:\{port}/jsonrpc"), parent.initializeAniAdd());

        subscriber.connect();

//        val server = new AnidbServer(port, parent.initializeAniAdd()).init();
//        server.start();
        return 0;
    }
}

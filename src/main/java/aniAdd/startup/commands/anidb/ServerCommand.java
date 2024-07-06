package aniAdd.startup.commands.anidb;

import aniAdd.server.AnidbServer;
import lombok.val;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "server", mixinStandardHelpOptions = true, version = "1.0",
        description = "Starts the server.")
public class ServerCommand implements Callable<Integer> {
    @CommandLine.Option(names = {"--port"}, description = "The port to listen on.")
    private int port = 8080;

    @CommandLine.ParentCommand
    private AnidbCommand parent;


    @Override
    public Integer call() throws Exception {
        System.out.println(STR."Should start server on port \{port}");

        val server = new AnidbServer(port, parent.initializeAniAdd()).init();
        server.start();
        return 0;
    }
}

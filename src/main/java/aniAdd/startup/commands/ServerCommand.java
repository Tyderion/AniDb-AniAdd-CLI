package aniAdd.startup.commands;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "server", mixinStandardHelpOptions = true, version = "1.0",
        description = "Starts the server.")
public class ServerCommand implements Callable<Integer> {
    @CommandLine.Option(names = {"--port"}, description = "The port to listen on.")
    private int port = 8080;


    @Override
    public Integer call() throws Exception {
        System.out.println(STR."Should start server on port \{port}");
        return 0;
    }
}

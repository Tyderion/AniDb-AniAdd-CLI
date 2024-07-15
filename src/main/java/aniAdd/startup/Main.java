package aniAdd.startup;

import aniAdd.startup.commands.CliCommand;

public class Main {

    static {
        LoggerConfig.configureLogger();
    }

    public static void main(String[] args) {
        new picocli.CommandLine(new CliCommand()).execute(args);
    }
}

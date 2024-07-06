package aniAdd.startup;

import aniAdd.startup.commands.CliCommand;

public class MainTwo {

    public static void main(String[] args) {
        new picocli.CommandLine(new CliCommand()).execute(args);
    }
}

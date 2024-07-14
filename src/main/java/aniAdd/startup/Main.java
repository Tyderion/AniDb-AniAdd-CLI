package aniAdd.startup;

import aniAdd.startup.commands.CliCommand;

public class Main {

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$s %2$s %5$s%6$s%n");
        new picocli.CommandLine(new CliCommand()).execute(args);
    }
}

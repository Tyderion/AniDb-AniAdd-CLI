package aniAdd.startup;

import aniAdd.startup.commands.CliCommand;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    public static void main(String[] args) {
        Logger.getGlobal().log(Level.WARNING, STR."Args: \{String.join(" ", args)}");
        new picocli.CommandLine(new CliCommand()).execute(args);
    }
}

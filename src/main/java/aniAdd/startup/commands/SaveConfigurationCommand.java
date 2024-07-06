package aniAdd.startup.commands;

import aniAdd.config.AniConfiguration;
import aniAdd.config.ConfigFileParser;
import picocli.CommandLine;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

@CommandLine.Command(name = "save", mixinStandardHelpOptions = true, version = "1.0",
        description = "Save the options to a new file which then can be edited (manually) and loaded by using -c")
public class SaveConfigurationCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The path to the file to save the configuration to.")
    private String path;

    @CommandLine.ParentCommand
    private CliCommand parent;

    @Override
    public Integer call() throws Exception {
        AniConfiguration config = parent.getConfiguration();
        try {
            ConfigFileParser<AniConfiguration> configParser =
                    new ConfigFileParser<>(path, AniConfiguration.class);
            configParser.saveToFile(config);
            Logger.getGlobal().log(Level.INFO, STR."Finished writing config to file: \{path}");
        } catch (IOException e) {
            Logger.getGlobal().log(Level.WARNING, STR."Could not write config to file: \{path}");
        }
        return 0;
    }
}

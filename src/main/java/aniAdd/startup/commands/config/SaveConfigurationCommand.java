package aniAdd.startup.commands.config;

import aniAdd.config.AniConfiguration;
import aniAdd.config.ConfigFileParser;
import aniAdd.startup.validation.validators.nonempty.NonEmpty;
import lombok.extern.java.Log;
import lombok.val;
import picocli.CommandLine;

import java.io.IOException;
import java.util.concurrent.Callable;

@Log
@CommandLine.Command(name = "save", mixinStandardHelpOptions = true, version = "1.0",
        description = "Save the options to a new file which then can be edited (manually) and loaded by using -c")
public class SaveConfigurationCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The path to the file to save the configuration to.")
    @NonEmpty
    private String path;

    @CommandLine.Option(names = { "--default" }, description = "Load default empty configuration if config path not found or set.", defaultValue = "false")
    private boolean useDefault;

    @CommandLine.ParentCommand
    private ConfigCommand parent;

    @Override
    public Integer call() throws Exception {
        val config = parent.getConfiguration(useDefault);
        config.ifPresentOrElse(conf -> {
            try {
                ConfigFileParser<AniConfiguration> configParser =
                        new ConfigFileParser<>(path, AniConfiguration.class);
                configParser.saveToFile(conf);
                log.info(STR."Finished writing config to file: \{path}");
            } catch (IOException e) {
                log.warning(STR."Could not write config to file: \{path}");
            }
        }, () -> log.warning("No configuration loaded"));

        return 0;
    }
}
package aniAdd.startup.commands.config;

import aniAdd.config.AniConfiguration;
import aniAdd.startup.commands.CliCommand;
import picocli.CommandLine;

@CommandLine.Command(
        subcommands = {SaveConfigurationCommand.class},
        name = "config",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Configuration handling")
public class ConfigCommand {

    @CommandLine.ParentCommand
    private CliCommand parent;

    public AniConfiguration getConfiguration() {
        return parent.getConfiguration();
    }
}

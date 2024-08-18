package aniAdd.startup.commands.config;

import aniAdd.config.AniConfiguration;
import aniAdd.startup.commands.CliCommand;
import lombok.Setter;
import picocli.CommandLine;

import java.util.Optional;

@CommandLine.Command(
        subcommands = {SaveConfigurationCommand.class},
        name = "config",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Configuration handling")
public class ConfigCommand {

    @CommandLine.Option(names = {"-c", "--config"}, description = "The path to the config file. Specified parameters will override values from the config file.", required = false, scope = CommandLine.ScopeType.INHERIT)
    @Setter String configPath;

    @CommandLine.ParentCommand
    private CliCommand parent;

    public Optional<AniConfiguration> getConfiguration(boolean useDefault) {
        return parent.getAniConfiguration(useDefault, configPath);
    }
}

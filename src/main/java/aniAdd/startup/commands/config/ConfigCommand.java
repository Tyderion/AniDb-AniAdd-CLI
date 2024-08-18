package aniAdd.startup.commands.config;

import aniAdd.startup.commands.CliCommand;
import lombok.Getter;
import picocli.CommandLine;

@Getter
@CommandLine.Command(
        subcommands = {SaveConfigurationCommand.class},
        name = "config",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Configuration handling")
public class ConfigCommand {

    @CommandLine.Option(names = {"-c", "--config"}, description = "The path to the config file. Specified parameters will override values from the config file.", required = false, scope = CommandLine.ScopeType.INHERIT)
    String configPath;

    @CommandLine.Option(names = {"--tagging-system"}, description = "the path to a file containing the Tagging System definition", required = false, scope = CommandLine.ScopeType.INHERIT)
    private String taggingSystem;
}

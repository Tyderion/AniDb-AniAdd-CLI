package aniAdd.startup.commands;

import aniAdd.config.AniConfiguration;
import aniAdd.config.ConfigFileLoader;
import aniAdd.startup.commands.anidb.AnidbCommand;
import aniAdd.startup.commands.debug.DebugCommand;
import aniAdd.startup.commands.debug.InsertDataCommand;
import aniAdd.startup.commands.config.ConfigCommand;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;

import java.util.Optional;

@Slf4j
@CommandLine.Command(name = "aniadd-cli.jar",
        mixinStandardHelpOptions = true,
        version = "1.0",
        scope = CommandLine.ScopeType.INHERIT,
        description = "The main command.",
        subcommands = {AnidbCommand.class, ConfigCommand.class, FileMoveCommand.class, TagsCommand.class, DebugCommand.class})
public class CliCommand {

    @CommandLine.Option(names = {"--tagging-system"}, description = "the path to a file containing the Tagging System definition", required = false, scope = CommandLine.ScopeType.INHERIT)
    String taggingSystem;

    public Optional<AniConfiguration> getConfiguration(boolean useDefault, String configPath) {
        val loader = new ConfigFileLoader(configPath, taggingSystem);
        return loader.getConfiguration(useDefault);
    }
}

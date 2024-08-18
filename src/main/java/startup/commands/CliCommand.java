package startup.commands;

import startup.commands.anidb.AnidbCommand;
import startup.commands.debug.DebugCommand;
import startup.commands.config.ConfigCommand;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Getter
@Slf4j
@CommandLine.Command(name = "aniadd-cli.jar",
        mixinStandardHelpOptions = true,
        version = "1.0",
        scope = CommandLine.ScopeType.INHERIT,
        description = "The main command.",
        subcommands = {AnidbCommand.class, ConfigCommand.class, FileMoveCommand.class, TagsCommand.class, DebugCommand.class})
public class CliCommand {
}

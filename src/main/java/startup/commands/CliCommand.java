package startup.commands;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import startup.commands.anidb.AnidbCommand;
import startup.commands.config.ConfigCommand;
import startup.commands.debug.DebugCommand;

@Getter
@Slf4j
@CommandLine.Command(name = "aniadd-cli.jar",
        mixinStandardHelpOptions = true,
        version = "1.0",
        scope = CommandLine.ScopeType.INHERIT,
        description = "The main command.",
        subcommands = {AnidbCommand.class, ConfigCommand.class, FileMoveCommand.class, TagsCommand.class, DebugCommand.class, RunCommand.class})
public class CliCommand {
}

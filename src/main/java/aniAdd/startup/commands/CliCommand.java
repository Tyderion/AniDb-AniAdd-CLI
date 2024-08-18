package aniAdd.startup.commands;

import aniAdd.config.AniConfiguration;
import aniAdd.config.AniConfigurationHandler;
import aniAdd.startup.commands.anidb.AnidbCommand;
import aniAdd.startup.commands.debug.DebugCommand;
import aniAdd.startup.commands.config.ConfigCommand;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;

import java.util.Optional;

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

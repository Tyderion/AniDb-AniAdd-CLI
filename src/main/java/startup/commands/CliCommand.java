package startup.commands;

import config.CliConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import startup.commands.anidb.AnidbCommand;
import startup.commands.config.ConfigCommand;
import startup.commands.debug.DebugCommand;
import startup.validation.validators.nonblank.NonBlank;
import utils.config.ConfigFileHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

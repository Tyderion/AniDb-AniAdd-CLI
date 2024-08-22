package startup.commands.debug;

import lombok.Getter;
import picocli.CommandLine;

import java.nio.file.Path;

@Getter
@CommandLine.Command(name = "debug",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Debugging commands",
        subcommands = {InsertResponse.class, FileMoveCommand.class})
public class DebugCommand {
    @CommandLine.Option(names = "--db", description = "The path to the sqlite db", required = false, defaultValue = "debug.sqlite", scope = CommandLine.ScopeType.INHERIT)
    Path dbPath;
}

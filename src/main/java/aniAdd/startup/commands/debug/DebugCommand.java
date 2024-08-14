package aniAdd.startup.commands.debug;

import lombok.Getter;
import picocli.CommandLine;

@Getter
@CommandLine.Command(name = "debug",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Debugging commands",
        subcommands = {InsertDataCommand.class})
public class DebugCommand {
    @CommandLine.Option(names = "--db", description = "The path to the sqlite db", required = false, defaultValue = "debug.sqlite", scope = CommandLine.ScopeType.INHERIT)
    String dbPath;
}

package startup.commands.config;

import lombok.Getter;
import picocli.CommandLine;

@Getter
@CommandLine.Command(
        subcommands = {NewCommand.class, ConvertCommand.class},
        name = "config",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Configuration handling")
public class ConfigCommand {
}

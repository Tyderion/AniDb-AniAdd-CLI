package startup.commands;

import config.CliConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import startup.validation.validators.nonempty.NonEmpty;
import utils.config.ConfigFileHandler;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Slf4j
@CommandLine.Command(name = "run",
        mixinStandardHelpOptions = true,
        scope = CommandLine.ScopeType.INHERIT,
        description = "Run with config file")
public class RunCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-r", "--run-config"}, description = "The path to the config file.", required = true, scope = CommandLine.ScopeType.INHERIT)
    private Path configPath;

    @CommandLine.Option(names = { "--default" }, description = "Use default configuration", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "false")
    private boolean useDefault;

    @Override
    public Integer call() throws Exception {
        val handler = new ConfigFileHandler<>(CliConfiguration.class);
        val config = handler.getConfiguration(configPath, false);

        return 0;
    }
}

package startup.commands;

import config.CliConfiguration;
import config.RunConfig;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import startup.validation.ValidatingExecutionStrategy;
import utils.config.ConfigFileHandler;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Slf4j
@CommandLine.Command(name = "run",
        mixinStandardHelpOptions = true,
        scope = CommandLine.ScopeType.INHERIT,
        description = "Run with config file")
public class RunCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-r", "--run-config"}, description = "The path to the config file. Defaults to $CONFIG_FILE", required = false, scope = CommandLine.ScopeType.INHERIT)
    private Path configPath;

    @Override
    public Integer call() throws Exception {
        val handler = new ConfigFileHandler<>(CliConfiguration.class);
        val env = System.getenv();
        if (configPath == null) {
            val configFile = env.get("CONFIG_FILE");
            if (configFile == null) {
                log.error("No config file provided. Set the CONFIG_FILE environment variable or use the -r option.");
                return 1;
            }
            configPath = Path.of(env.get("CONFIG_FILE"));
        }

        val config = handler.getConfiguration(configPath);
        if (config == null) {
            log.error(STR."Failed to load configuration from \{configPath}");
            return 1;
        }
        if (config.run() == null) {
            log.error(STR."No run configuration found in the config file. \{configPath}");
            return 1;
        }
        try {
            val command = config.run().toCommandArgs(configPath);
            return new picocli.CommandLine(new CliCommand())
                    .setExecutionStrategy(new ValidatingExecutionStrategy())
                    .execute(command.toArray(String[]::new));
        } catch (RunConfig.InvalidConfigException e) {
            log.error(STR."Invalid run configuration: \{e.getMessage()}");
            return 1;
        }
    }
}

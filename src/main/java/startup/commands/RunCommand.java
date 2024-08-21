package startup.commands;

import config.RunConfig;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import startup.validation.ConfigValidatingExecutionStrategy;
import utils.config.SecretsLoader;

import java.util.concurrent.Callable;

@Slf4j
@CommandLine.Command(name = "run",
        mixinStandardHelpOptions = true,
        scope = CommandLine.ScopeType.INHERIT,
        description = "Run with config file")
public class RunCommand implements Callable<Integer> {
    @CommandLine.ParentCommand
    private CliCommand parent;

    @Override
    public Integer call() throws Exception {
        val config = parent.getConfiguration();
        if (config == null) {
            return 1;
        }
        if (config.run() == null) {
            log.error(STR."No run configuration found in the config file. \{parent.getConfigPath()}");
            return 1;
        }
        try {
            val command = config.run().toCommandArgs(parent.getConfigPath(), new SecretsLoader());
            return new picocli.CommandLine(new CliCommand())
                    .setExecutionStrategy(new ConfigValidatingExecutionStrategy())
                    .execute(command.toArray(String[]::new));
        } catch (RunConfig.InvalidConfigException e) {
            log.error(STR."Invalid run configuration: \{e.getMessage()}");
            return 1;
        }
    }
}

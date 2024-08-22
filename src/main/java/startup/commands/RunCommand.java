package startup.commands;

import config.RunConfig;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import startup.validation.ConfigValidatingExecutionStrategy;

import java.util.concurrent.Callable;

@Slf4j
@CommandLine.Command(name = "run",
        mixinStandardHelpOptions = true,
        scope = CommandLine.ScopeType.INHERIT,
        description = "Run with config file")
public class RunCommand extends ConfigRequiredCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        val config = getConfiguration();
        if (config == null) {
            return 1;
        }
        if (config.run() == null) {
            log.error(STR."No run configuration found in the config file. \{configPath}");
            return 1;
        }
        try {
            val command = config.run().toCommandArgs(configPath);
            return new picocli.CommandLine(new CliCommand())
                    .setExecutionStrategy(new ConfigValidatingExecutionStrategy())
                    .execute(command.toArray(String[]::new));
        } catch (RunConfig.InvalidConfigException e) {
            log.error(STR."Invalid run configuration: \{e.getMessage()}");
            return 1;
        }
    }
}

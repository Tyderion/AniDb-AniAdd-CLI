package startup.commands;

import config.RunConfig;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import startup.validation.ConfigValidatingExecutionStrategy;
import startup.validation.validators.config.FromConfig;

import java.util.concurrent.Callable;

@Slf4j
@CommandLine.Command(name = "run",
        mixinStandardHelpOptions = true,
        scope = CommandLine.ScopeType.INHERIT,
        description = "Run with config file")
public class RunCommand extends ConfigRequiredCommand implements Callable<Integer> {
    @FromConfig(configPath = "run", required = true)
    private RunConfig runConfig;

    @Override
    public Integer call() throws Exception {
        try {
            val command = runConfig.toCommandArgs(configPath);
            log.info(STR."Running command: \{String.join(" ", command)}");
            return new picocli.CommandLine(new CliCommand())
                    .setExecutionStrategy(new ConfigValidatingExecutionStrategy())
                    .execute(command.toArray(String[]::new));
        }
        catch (RunConfig.InvalidConfigException e) {
            log.error(STR."Invalid run configuration: \{e.getMessage()}");
            throw e;
        }
    }
}

package startup;

import startup.commands.CliCommand;
import startup.validation.ConfigValidatingExecutionStrategy;

public class Main {

    static {
        LoggerConfig.configureLogger();
    }

    public static void main(String[] args) {
        new picocli.CommandLine(new CliCommand())
                .setExecutionStrategy(new ConfigValidatingExecutionStrategy())
                .execute(args);
    }
}

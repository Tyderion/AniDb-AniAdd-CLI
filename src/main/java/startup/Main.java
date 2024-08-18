package startup;

import startup.commands.CliCommand;
import startup.validation.ValidatingExecutionStrategy;

public class Main {

    static {
        LoggerConfig.configureLogger();
    }

    public static void main(String[] args) {
        new picocli.CommandLine(new CliCommand())
                .setExecutionStrategy(new ValidatingExecutionStrategy())
                .execute(args);
    }
}

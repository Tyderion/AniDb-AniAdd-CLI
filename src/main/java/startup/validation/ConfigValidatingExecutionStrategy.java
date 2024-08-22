package startup.validation;

import config.CliConfiguration;
import picocli.CommandLine;
import startup.commands.CliCommand;
import startup.commands.ConfigCommand;

public class ConfigValidatingExecutionStrategy implements CommandLine.IExecutionStrategy {
    public int execute(CommandLine.ParseResult parseResult) {
        validateRootParseResult(parseResult);
        return new CommandLine.RunLast().execute(parseResult); // default execution strategy
    }

    private void validateRootParseResult(CommandLine.ParseResult parseResult) {
        if (CliCommand.class != parseResult.commandSpec().userObject().getClass()) {
            return;
        }
        validateParseResult(parseResult, null);
    }

    void validateParseResult(CommandLine.ParseResult parseResult, CliConfiguration configuration) {
        if (configuration == null && parseResult.commandSpec().userObject() instanceof ConfigCommand command) {
            configuration = command.getConfiguration();
        }
        Validator.validate(parseResult.commandSpec(), configuration);
        if (parseResult.subcommand() != null) {
            validateParseResult(parseResult.subcommand(), configuration);
        }
    }
}
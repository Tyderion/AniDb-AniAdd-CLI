package startup.validation;

import config.CliConfiguration;
import picocli.CommandLine;
import startup.commands.CliCommand;

public class ConfigValidatingExecutionStrategy implements CommandLine.IExecutionStrategy {
    public int execute(CommandLine.ParseResult parseResult) {
        validateParseResult(parseResult);
        return new CommandLine.RunLast().execute(parseResult); // default execution strategy
    }

    private void validateParseResult(CommandLine.ParseResult parseResult) {
        if (CliCommand.class != parseResult.commandSpec().userObject().getClass()) {
            return;
        }
        validateParseResult(parseResult, ((CliCommand) parseResult.commandSpec().userObject()).getConfiguration());
    }

    void validateParseResult(CommandLine.ParseResult parseResult, CliConfiguration configuration) {
        Validator.validate(parseResult.commandSpec(), configuration);
        if (parseResult.subcommand() != null) {
            validateParseResult(parseResult.subcommand(), configuration);
        }
    }
}
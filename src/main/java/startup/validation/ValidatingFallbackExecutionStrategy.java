package startup.validation;

import config.CliConfiguration;
import picocli.CommandLine;

public class ValidatingFallbackExecutionStrategy implements CommandLine.IExecutionStrategy {
    public int execute(CommandLine.ParseResult parseResult) {
        validateParseResult(parseResult);
        return new CommandLine.RunLast().execute(parseResult); // default execution strategy
    }

    void validateParseResult(CommandLine.ParseResult parseResult) {
        Validator.validateAndApplyFallbacks(parseResult.commandSpec());
        if (parseResult.subcommand() != null) {
            validateParseResult(parseResult.subcommand());
        }
    }
}
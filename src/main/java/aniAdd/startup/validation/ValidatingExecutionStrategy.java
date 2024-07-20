package aniAdd.startup.validation;

import picocli.CommandLine;

public class ValidatingExecutionStrategy implements CommandLine.IExecutionStrategy {
    public int execute(CommandLine.ParseResult parseResult) {

        validateParseResult(parseResult);
        return new CommandLine.RunLast().execute(parseResult); // default execution strategy
    }

    void validateParseResult(CommandLine.ParseResult parseResult) {
        Validator.validate(parseResult.commandSpec());
        if (parseResult.subcommand() != null) {
            validateParseResult(parseResult.subcommand());
        }
    }
}
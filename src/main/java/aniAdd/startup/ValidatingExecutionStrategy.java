package aniAdd.startup;

import aniAdd.startup.commands.IValidate;
import picocli.CommandLine;

class ValidatingExecutionStrategy implements CommandLine.IExecutionStrategy {
    public int execute(CommandLine.ParseResult parseResult) {

        validateParseResult(parseResult);
        return new CommandLine.RunLast().execute(parseResult); // default execution strategy
    }

    void validateParseResult(CommandLine.ParseResult parseResult) {
        if (parseResult.commandSpec().userObject() instanceof IValidate validator) {
            validator.validate();
        }
        if (parseResult.subcommand() != null) {
            validateParseResult(parseResult.subcommand());
        }
    }
}
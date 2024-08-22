package startup.validation;

import config.RootConfiguration;
import picocli.CommandLine;
import startup.commands.CliCommand;
import startup.commands.ConfigRequiredCommand;

public class ConfigValidatingExecutionStrategy implements CommandLine.IExecutionStrategy {
    private CommandLine.ParseResult cliCommandParseResult;
    public int execute(CommandLine.ParseResult parseResult) {
        validateRootParseResult(parseResult);
        return new CommandLine.RunLast().execute(parseResult); // default execution strategy
    }

    private void validateRootParseResult(CommandLine.ParseResult parseResult) {
        if (CliCommand.class != parseResult.commandSpec().userObject().getClass()) {
            return;
        }
        cliCommandParseResult = parseResult;
        validateParseResult(parseResult, null);
    }

    void validateParseResult(CommandLine.ParseResult parseResult, RootConfiguration configuration) {
        if (configuration == null && parseResult.commandSpec().userObject() instanceof ConfigRequiredCommand command) {
            if (!command.configPresent()) {
                throw new CommandLine.ParameterException(cliCommandParseResult.commandSpec().commandLine(), command.getError());
            }
            configuration = command.getConfiguration();
        }
        Validator.validate(parseResult.commandSpec(), configuration);
        if (parseResult.subcommand() != null) {
            validateParseResult(parseResult.subcommand(), configuration);
        }
    }
}
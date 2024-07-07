package udpapi2.command;

import udpapi2.QueryId;

public class LogoutCommand extends CommandWrapper {
    private static final String ACTION = "LOGOUT";

    private LogoutCommand(Command command) throws IllegalArgumentException {
        super(command);
    }

    public static LogoutCommand Create() {
        return new LogoutCommand(Command.builder()
                .action(ACTION)
                .identifier(ACTION.toLowerCase())
                .queryId(QueryId.Next())
                .needsLogin(false)
                .tag(null)
                .build());
    }
}

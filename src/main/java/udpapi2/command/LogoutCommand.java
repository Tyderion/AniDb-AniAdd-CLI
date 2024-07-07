package udpapi2.command;

import udpapi2.QueryId;

public class LogoutCommand extends CommandWrapper {
    private static final String LOGOUT_ACTION = "LOGOUT";

    private LogoutCommand(Command command) throws IllegalArgumentException {
        super(command);
    }

    public static LogoutCommand Create() {
        return new LogoutCommand(Command.builder()
                .action(LOGOUT_ACTION)
                .identifier(LOGOUT_ACTION.toLowerCase())
                .queryId(QueryId.Next())
                .needsLogin(false)
                .tag(null)
                .build());
    }
}

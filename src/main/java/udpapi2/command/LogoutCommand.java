package udpapi2.command;

import lombok.experimental.SuperBuilder;
import udpapi2.QueryId;

@SuperBuilder
public class LogoutCommand extends Command {
    private static final String ACTION = "LOGOUT";

    public static LogoutCommand Create() {
        return LogoutCommand.builder()
                .action(ACTION)
                .identifier(ACTION.toLowerCase())
                .queryId(QueryId.Next())
                .needsLogin(false)
                .tag(null)
                .build();
    }
}

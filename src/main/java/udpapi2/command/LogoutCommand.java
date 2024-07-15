package udpapi2.command;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import udpapi2.QueryId;

@Getter
@SuperBuilder
public class LogoutCommand extends Command {
    private static final String ACTION = "LOGOUT";
    boolean isAutomatic;

    public static LogoutCommand Create(boolean isAutomatic) {
        return LogoutCommand.builder()
                .action(ACTION)
                .identifier(ACTION.toLowerCase())
                .queryId(QueryId.Next())
                .needsLogin(false)
                .tag(null)
                .isAutomatic(isAutomatic)
                .build();
    }
}

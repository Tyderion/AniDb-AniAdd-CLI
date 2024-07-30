package udpapi.command;

import lombok.experimental.SuperBuilder;
import udpapi.QueryId;

@SuperBuilder
public class PingCommand extends Command {
    private static final String ACTION = "PING";

    public static PingCommand Create() {
        return PingCommand.builder()
                .action(ACTION)
                .identifier(ACTION.toLowerCase())
                .queryId(QueryId.Next())
                .needsLogin(false)
                .tag(null)
                .parameter("nat", "1")
                .build();
    }
}

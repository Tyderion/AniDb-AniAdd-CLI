package udpapi2.command;

import udpapi2.QueryId;
import udpapi2.UdpApi;

public class PingCommand extends CommandWrapper {
    private static final String PING_ACTION = "PING";

    private PingCommand(Command command) throws IllegalArgumentException {
        super(command);
    }

    public static PingCommand Create() {
        return new PingCommand(Command.builder()
                .action(PING_ACTION)
                .identifier(PING_ACTION.toLowerCase())
                .queryId(QueryId.Next())
                .needsLogin(false)
                .tag(null)
                .parameter("nat", "1")
                .build());
    }
}

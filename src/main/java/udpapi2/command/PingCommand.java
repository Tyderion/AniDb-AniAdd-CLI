package udpapi2.command;

import udpapi2.QueryId;

public class PingCommand extends CommandWrapper {
    private static final String ACTION = "PING";

    private PingCommand(Command command) throws IllegalArgumentException {
        super(command);
    }

    public static PingCommand Create() {
        return new PingCommand(Command.builder()
                .action(ACTION)
                .identifier(ACTION.toLowerCase())
                .queryId(QueryId.Next())
                .needsLogin(false)
                .tag(null)
                .parameter("nat", "1")
                .build());
    }
}

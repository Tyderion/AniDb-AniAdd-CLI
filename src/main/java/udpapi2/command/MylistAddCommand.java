package udpapi2.command;

import lombok.val;
import udpapi2.QueryId;

public class MylistAddCommand extends CommandWrapper {
    private static final String ACTION = "MYLIISTADD";

    private MylistAddCommand(Command command) throws IllegalArgumentException {
        super(command);
    }

    public static MylistAddCommand Create(int fileId, long length, String ed2k, int state, boolean watched) {
        val command = Command.builder()
                .action(ACTION)
                .identifier("mladd")
                .queryId(QueryId.Next())
                .needsLogin(true)
                .tag(String.valueOf(fileId))
                .parameter("size", String.valueOf(length))
                .parameter("ed2k", ed2k)
                .parameter("state", Integer.toString(state));
        if (watched) {
            command.parameter("viewed", "1");
        }

        return new MylistAddCommand(command.build());
    }

    public MylistAddCommand WithEdit() {
        return new MylistAddCommand(this.getCommand().toBuilder()
                .parameter("edit", "1")
                .build());
    }
}

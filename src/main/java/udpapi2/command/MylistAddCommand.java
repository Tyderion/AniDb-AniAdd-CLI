package udpapi2.command;

import lombok.experimental.SuperBuilder;
import lombok.val;
import udpapi2.QueryId;

@SuperBuilder(toBuilder = true)
public class MylistAddCommand extends Command {
    private static final String ACTION = "MYLIISTADD";

    public static MylistAddCommand Create(int fileId, long length, String ed2k, int state, boolean watched) {
        val command = MylistAddCommand.builder()
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

        return command.build();
    }

    public MylistAddCommand WithEdit() {
        return this.toBuilder()
                .parameter("edit", "1")
                .build();
    }
}

package udpapi.command;

import lombok.experimental.SuperBuilder;
import lombok.val;
import udpapi.QueryId;

@SuperBuilder(toBuilder = true)
public class MylistCommand extends Command {
    private static final String ACTION = "MYLIST";

    public static MylistCommand Create(int fileId, long length, String ed2k) {
        val command = MylistCommand.builder()
                .action(ACTION)
                .identifier("ml")
                .queryId(QueryId.Next())
                .needsLogin(true)
                .tag(fileId)
                .parameter("size", String.valueOf(length))
                .parameter("ed2k", ed2k);

        return command.build();
    }
}

package udpapi.command;

import lombok.experimental.SuperBuilder;
import lombok.val;
import processing.FileInfo;
import udpapi.QueryId;
import udpapi.reply.Reply;
import udpapi.reply.ReplyStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@SuperBuilder(toBuilder = true)
public class MylistCommand extends Command {
    private static final String ACTION = "MYLIST";

    public static MylistCommand Create(String ed2k, long fileSize, int tag) {
        val command = MylistCommand.builder()
                .action(ACTION)
                .identifier("ml")
                .queryId(QueryId.Next())
                .needsLogin(true)
                .tag(tag)
                .parameter("size", String.valueOf(fileSize))
                .parameter("ed2k", ed2k);

        return command.build();
    }

    public static MylistCommand Create(long fileId, int tag) {
        val command = MylistCommand.builder()
                .action(ACTION)
                .identifier("ml")
                .queryId(QueryId.Next())
                .needsLogin(true)
                .tag(tag)
                .parameter("fid", String.valueOf(fileId));
        return command.build();
    }

    public static void setWatchedDate(Reply reply, FileInfo fileInfo) {
        if (reply.getReplyStatus() == ReplyStatus.MYLIST) {
            val watchedTimestamp = Long.parseLong(reply.getResponseData().get(7));
            if (watchedTimestamp > 0) {
                fileInfo.setWatchedDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(watchedTimestamp), ZoneId.systemDefault()));
            }
        }
    }
}

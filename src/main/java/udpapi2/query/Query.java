package udpapi2.query;

import lombok.Data;
import udpapi2.command.CommandWrapper;
import udpapi2.reply.Reply;

import java.util.Date;

@Data
public class Query {
    private final CommandWrapper command;
    private Reply reply;
    private Date sentAt;
    private Date receivedAt;
    private int retries;
    private boolean success;
}

package udpapi2.query;

import lombok.Data;
import lombok.val;
import udpapi2.command.CommandWrapper;
import udpapi2.reply.Reply;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Data
public class Query {
    private final CommandWrapper command;
    private Reply reply;
    private final Date sentAt;
    private Date receivedAt;

    private int retries;

    public boolean isSuccess() {
        return reply != null;
    }

    public String getFullTag() {
        return command.getFullTag();
    }

    public String getTag() {
        return command.getTag();
    }

    public DatagramPacket getBytes(String session, InetAddress ip, int port) {
        // Encoding??
        val data =  command.getCommand().toString(session).getBytes(StandardCharsets.US_ASCII);
        return new DatagramPacket(data, data.length, ip, port);
    }

    public boolean success() {
        return reply != null && reply.getReplyStatus().success();
    }
}

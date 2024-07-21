package udpapi;

import aniAdd.misc.Misc;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;
import udpapi.reply.Reply;
import udpapi.reply.ReplyStatus;

import java.util.Arrays;
import java.util.stream.Collectors;

@Log
@RequiredArgsConstructor
public class ParseReply implements Runnable {

    final Integration api;
    final String message;

    @Override
    public void run() {
        try {
            parseReply();
        } catch (Exception e) {
            log.warning(STR."Parse Error: \{e.toString()}");
            e.printStackTrace();
        }
    }

    private static String aniDbDecode(String str) {
        return str.replaceAll("<br />", "\n")
                .replaceAll("`", "'")
                .replaceAll("/", "|");
    }

    private void parseReply() {
        if (message == null || message.isEmpty()) {
            log.warning("Server reply is an empty string... ignoring");
            return;
        }
        log.fine( STR."Reply: \{message.replace("\n", " \\n ")}");
// LOGIN: auth-0 200 i6qdp 94.101.114.107:3333 LOGIN ACCEPTED\n

        val builder = Reply.builder().fullMessage(message);
        val parts = message.split(" ");
        val tag = parts[0];
        builder.fullTag(tag);

        if (Misc.isNumber(parts[1])) {
            builder.replyStatus(ReplyStatus.fromString(parts[1]));
        }

        val lines = message.split("\n");

        if (lines.length == 1) {
            // No data fields, i.e. AUTH or LOGOUT
            // auth-0 200 i6qdp 94.101.114.107:3333 LOGIN ACCEPTED\n
            // logout-0 203 LOGGED OUT\n
            for (int i = 2; i < parts.length; i++) {
                builder.value(parts[i]);
            }
            api.addReply(builder.build());
            return;
        }

        builder.responseData(
                Arrays.stream(lines).skip(1)
                        .flatMap(line -> Arrays.stream(line.split("\\|")).map(ParseReply::aniDbDecode))
                        .collect(Collectors.toList())
        );

        api.addReply(builder.build());
    }

    public interface Integration {
        void addReply(Reply reply);
    }
}
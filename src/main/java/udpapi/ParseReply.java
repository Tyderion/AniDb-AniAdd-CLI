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
        log.fine(STR."Reply: \{message.replace("\n", "\\n")}");

        val builder = Reply.builder().fullMessage(message);
        val parts = message.split(" ");
        val tagOrStatus = parts[0];
        val statusOrMessage = parts[1];
        if (tagOrStatus.matches("[0-9]{3}")) {
            builder.replyStatus(ReplyStatus.fromString(tagOrStatus));
        } else {
            builder.fullTag(tagOrStatus);
            if (statusOrMessage.matches("[0-9]{3}")) {
                builder.replyStatus(ReplyStatus.fromString(statusOrMessage));
            }
        }

        val lines = message.split("\n");

        if (lines.length == 1) {
            // No data fields, i.e. AUTH or LOGOUT
            for (int i = 2; i < parts.length; i++) {
                builder.value(parts[i]);
            }
        } else {
            builder.responseData(
                    Arrays.stream(lines).skip(1)
                            .flatMap(line -> Arrays.stream(line.split("\\|")).map(ParseReply::aniDbDecode))
                            .collect(Collectors.toList())
            );
        }

        api.addReply(builder.build());
    }

    public interface Integration {
        void addReply(Reply reply);
    }
}

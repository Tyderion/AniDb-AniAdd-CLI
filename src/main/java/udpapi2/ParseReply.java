package udpapi2;

import aniAdd.Communication;
import lombok.RequiredArgsConstructor;

import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class ParseReply implements  Runnable{

    final UdpApi api;
    final String message;
    @Override
    public void run() {
        try {
            parseReply(message);
        } catch (Exception e) {
            Logger.getGlobal().log(Level.WARNING, STR."Parse Error: \{e.toString()}");
        }
    }

    private void parseReply(String msg) {
        if (msg == null || msg.isEmpty()) {
            Logger.getGlobal().log(Level.INFO, "Server reply is an empty string... ignoring");
            return;
        }
        Logger.getGlobal().log(Level.INFO, STR."Reply:\{msg.replace("\n", " \\n ")}");

//        Reply.ReplyBuilder reply = Reply.builder();
//        int Pos;
//
//        if (!Misc.isNumber(msg.substring(0, 3))) {
//            Pos = msg.indexOf("-");
//            reply.Identifier(msg.substring(0, Pos));
//            if (reply.Identifier().contains(":")) {
//                reply.Tag(reply.Identifier().split(":")[1]);
//                reply.Identifier(reply.Identifier().split(":")[0]);
//            }
//
//            msg = msg.substring(Pos + 1);
//
//            Pos = msg.indexOf(" ");
//            reply.QueryId(Integer.parseInt(msg.substring(0, Pos)));
//            msg = msg.substring(Pos + 1);
//
//        } else {
//            reply.QueryId(serverReplies.size());
//            reply.Identifier("[SERVER]");
//        }
//
//        Pos = msg.indexOf(" ");
//        reply.ReplyId(Integer.parseInt(msg.substring(0, Pos)));
//        msg = msg.substring(Pos + 1);
//
//        Pos = msg.indexOf("\n");
//        reply.ReplyMsg(msg.substring(0, Pos));
//        msg = msg.substring(Pos + 1);
//
//        if (msg.endsWith("\n")) {
//            msg = msg.substring(0, msg.length() - 1);
//        }
//
//        if (msg.contains("|")) {
//            String[] dataFields;
//            if (msg.indexOf("\n") != msg.lastIndexOf("\n")) {
//                dataFields = msg.split("\n");
//            } else {
//                //wierd splitting function: if last field empty, it is omitted. Adding a space & & delete it again after splitting
//                dataFields = (msg + " ").split("\\|");
//                int i = dataFields.length - 1;
//                dataFields[i] = dataFields[i].substring(0, dataFields[i].length() - 1);
//            }
//
//            for (String dataField : dataFields) {
//                reply.DataField().add(dataField);
//            }
//
//        } else if (!msg.isEmpty()) {
//            reply.DataField().add(msg);
//        }
//
//        if (!reply.Identifier().equals("[SERVER]")) {
//            queries.get(reply.QueryId()).setReply(reply);
//            queries.get(reply.QueryId()).setReplyOn(new Date());
//            queries.get(reply.QueryId()).setSuccess(true);
//        } else {
//            serverReplies.add(reply);
//        }
//
//        Log(Communication.CommunicationEvent.EventType.Information, "Reply", (!reply.Identifier().equals("[SERVER]")) ? reply.QueryId() : ~reply.QueryId(), false);
//        deliverReply(reply);
    }
}

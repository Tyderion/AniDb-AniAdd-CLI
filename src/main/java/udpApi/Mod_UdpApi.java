package udpApi;

import aniAdd.IAniAdd;
import aniAdd.Modules.BaseModule;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.TreeMap;

import aniAdd.config.AniConfiguration;
import aniAdd.misc.ICallBack;
import aniAdd.misc.Misc;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class Mod_UdpApi extends BaseModule {

    public final int MAXRETRIES = 2;
    public final int CLIENTVER = 3;
    public final int PROTOCOLVER = 3;
    public final String CLIENTTAG = "AniAddCLI";
    public final String ANIDBAPIHOST = "api.anidb.info";
    public final int ANIDBAPIPORT = 9000;
    public final String NODELAY = ""; //"FILE,ANIME,MYLISTADD";
    private InetAddress aniDBIP;
    private boolean isEncodingSet;
    private DatagramSocket com;
    private String userName;
    private String password;
    private String autoPass;
    private String session;
    private String aniDBsession;
    private int currentPort;
    private int pingBackOffInMillis = 5 * 60000;
    private int pingBackoffCount = 1;
    static private int MAX_PING_BACKOFF_COUNT = 5;
    private boolean pingQueued = false;
    private Date lastCommandSentAt;
    private boolean connected, shutdown;
    private boolean banned;
    private boolean aniDBAPIDown;
    private boolean auth, isAuthed;
    private final ArrayList<Query> queries = new ArrayList<>();
    private final ArrayList<Reply> serverReplies = new ArrayList<Reply>();
    private final ArrayList<Cmd> cmdToSend = new ArrayList<Cmd>();
    private Date lastDelayPackageMills;
    private Date lastReplyPackage;
    private int replyHeadStart = 0; //TODO: Change handling
    private Thread send = new Thread(new Send());
    private Thread receive = new Thread(new Receive());
    private Idle idleClass = new Idle(); //-_-
    private Thread idle = new Thread(idleClass);
    private final TreeMap<String, ICallBack<Integer>> eventList = new TreeMap<String, ICallBack<Integer>>();

    public ArrayList<Query> Queries() {
        return queries;
    }

    public ArrayList<Reply> ServerReplies() {
        return serverReplies;
    }

    public Mod_UdpApi() {
        try {
            registerEvent(this::InternalMsgHandling, "auth", "logout", "ping");
            registerEvent(this::InternalMsgHandlingError, "501", "502", "505", "506", "555", "598", "600", "601", "602");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseReply(String msg) {
        if (msg == null || msg.isEmpty()) {
            Log(CommunicationEvent.EventType.Debug, "Server reply is an empty string... ignoring");
            return;
        }
        Log(CommunicationEvent.EventType.Debug, "Reply:" + msg.replace("\n", " \\n "));

        Reply reply = new Reply();
        int Pos;

        if (!Misc.isNumber(msg.substring(0, 3))) {
            Pos = msg.indexOf("-");
            reply.Identifier(msg.substring(0, Pos));
            if (reply.Identifier().contains(":")) {
                reply.Tag(reply.Identifier().split(":")[1]);
                reply.Identifier(reply.Identifier().split(":")[0]);
            }

            msg = msg.substring(Pos + 1);

            Pos = msg.indexOf(" ");
            reply.QueryId(Integer.parseInt(msg.substring(0, Pos)));
            msg = msg.substring(Pos + 1);

        } else {
            reply.QueryId(serverReplies.size());
            reply.Identifier("[SERVER]");
        }

        Pos = msg.indexOf(" ");
        reply.ReplyId(Integer.parseInt(msg.substring(0, Pos)));
        msg = msg.substring(Pos + 1);

        Pos = msg.indexOf("\n");
        reply.ReplyMsg(msg.substring(0, Pos));
        msg = msg.substring(Pos + 1);

        if (msg.endsWith("\n")) {
            msg = msg.substring(0, msg.length() - 1);
        }

        if (msg.contains("|")) {
            String[] dataFields;
            if (msg.indexOf("\n") != msg.lastIndexOf("\n")) {
                dataFields = msg.split("\n");
            } else {
                //wierd splitting function: if last field empty, it is omitted. Adding a space & & delete it again after splitting
                dataFields = (msg + " ").split("\\|");
                int i = dataFields.length - 1;
                dataFields[i] = dataFields[i].substring(0, dataFields[i].length() - 1);
            }

            for (String dataField : dataFields) {
                reply.DataField().add(dataField);
            }

        } else if (!msg.isEmpty()) {
            reply.DataField().add(msg);
        }

        if (!reply.Identifier().equals("[SERVER]")) {
            queries.get(reply.QueryId()).setReply(reply);
            queries.get(reply.QueryId()).setReplyOn(new Date());
            queries.get(reply.QueryId()).setSuccess(true);
        } else {
            serverReplies.add(reply);
        }

        Log(CommunicationEvent.EventType.Information, "Reply", (!reply.Identifier().equals("[SERVER]")) ? reply.QueryId() : ~reply.QueryId(), false);
        deliverReply(reply);
    }

    private void deliverReply(Reply reply) {
        ICallBack<Integer> replyFunc = eventList.get(reply.ReplyId().toString());
        if (replyFunc == null) {
            replyFunc = eventList.get(reply.Identifier());
        }
        if (replyFunc != null) {
            replyFunc.invoke((!reply.Identifier().equals("[SERVER]")) ? reply.QueryId() : ~reply.QueryId());
        } else {
            Log(CommunicationEvent.EventType.Debug, "Reply couldn't be delivered (unhandled reply)");
        }
    }

    public boolean authenticate() {
        Log(CommunicationEvent.EventType.Debug, "Authenticating");

        boolean hasUserInfo, hasPass, hasAniDBSession, hasAutoPass;
        hasUserInfo = userName != null && !userName.isEmpty();
        hasPass = password != null && !password.isEmpty();
        hasAniDBSession = aniDBsession != null && !aniDBsession.isEmpty();
        hasAutoPass = autoPass != null && !autoPass.isEmpty();

        if (!hasUserInfo || !(hasPass || hasAniDBSession || hasAutoPass)) {
            Log(CommunicationEvent.EventType.Error, "UserName or Password not set. (Aborting)");
            return false;
        }

        if (!connected) {
            if (com != null) com.close();

            if (!connect()) {
                return false;
            }
        }

        if (idle.getState() == java.lang.Thread.State.NEW) {
            Log(CommunicationEvent.EventType.Debug, "Starting Idle thread");
            idle.start();
        } else if (idle.getState() == java.lang.Thread.State.TERMINATED) {
            Log(CommunicationEvent.EventType.Debug, "Restarting Idle thread");
            idleClass = new Idle();
            idle = new Thread(idleClass);
            idle.start();
        }

        if (receive.getState() == java.lang.Thread.State.NEW) {
            Log(CommunicationEvent.EventType.Debug, "Starting Receive Thread");
            receive.start();
        } else if (receive.getState() == java.lang.Thread.State.TERMINATED) {
            Log(CommunicationEvent.EventType.Debug, "Restarting Receive thread");
            receive = new Thread(new Receive());
            receive.start();
        }
        auth = true;
        Cmd cmd = new Cmd("AUTH", "auth", null, false);
        if (aniDBsession != null && !aniDBsession.isEmpty()) {
            cmd.setArgs("sess", aniDBsession);
        }
        if (password != null && !password.isEmpty()) {
            cmd.setArgs("pass", password);
        }
        if (autoPass != null && !autoPass.isEmpty()) {
            cmd.setArgs("autopass", autoPass);
        }


        cmd.setArgs("user", userName.toLowerCase());
        cmd.setArgs("protover", Integer.toString(PROTOCOLVER), "client", CLIENTTAG.toLowerCase(), "clientver", Integer.toString(CLIENTVER));
        cmd.setArgs("nat", "1", "enc", "UTF8", "comp", "1");

        synchronized (cmdToSend) {
            for (int i = cmdToSend.size() - 1; i >= 0; i--) {
                if (cmdToSend.get(i).Action().equals("AUTH")) {
                    cmdToSend.remove(i);
                    Log(CommunicationEvent.EventType.Debug, "Pending (old) Authentication removed");
                }
            }
        }

        Log(CommunicationEvent.EventType.Debug, "Adding Authentication Cmd to queue");
        queryCmd(cmd);
        return true;
    }

    private void ping() {
        // Ping
        Cmd cmd = new Cmd("PING", "ping", null, false);
        cmd.setArgs("nat", "1");

        synchronized (cmdToSend) {
            for (int i = cmdToSend.size() - 1; i >= 0; i--) {
                if (cmdToSend.get(i).Action().equals("PING")) {
                    cmdToSend.remove(i);
                    Log(CommunicationEvent.EventType.Debug, "Pending (old) Ping removed");
                }
            }
        }

        Log(CommunicationEvent.EventType.Debug, "Adding Ping Cmd to queue");
        queryCmd(cmd);
    }

    public boolean connect() {
        try {
            aniDBIP = java.net.InetAddress.getByName(ANIDBAPIHOST);
            com = new java.net.DatagramSocket(ANIDBAPIPORT);
            connected = true;
            return true;
        } catch (Exception e) {
            Log(CommunicationEvent.EventType.Error, "Couldn't open connection. (Client may be running twice)");
            return false;
        }
    }


    public void queryCmd(Cmd cmd) {
        if (cmd == null) {
            Log(CommunicationEvent.EventType.Warning, "cmd cannot be a null reference... (ignored)");
            return;
        }

        synchronized (cmdToSend) {
            cmdToSend.add(cmd);
            Log(CommunicationEvent.EventType.Debug, "Added " + cmd.Action() + " cmd to queue");
        }

        if (send.getState() == java.lang.Thread.State.NEW) {
            Log(CommunicationEvent.EventType.Debug, "Starting Send thread");
            send.start();
        } else if (send.getState() == java.lang.Thread.State.TERMINATED) {
            Log(CommunicationEvent.EventType.Debug, "Restarting Send thread");
            send = new Thread(new Send());
            send.start();
        }
    }

    public void registerEvent(ICallBack<Integer> reply, String... events) {
        StringBuilder evtLst = new StringBuilder(" ");
        for (String evt : events) {
            if (reply != null) {
                eventList.put(evt, reply);
                evtLst.append(evt).append(" ");
            }
        }
        Log(CommunicationEvent.EventType.Debug, "Registered Events (" + evtLst + ") for " + reply.getClass().getName());
    }

    public void logOut() {
        logOut(true);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setAniDBSession(String aniDBsession) {
        this.aniDBsession = aniDBsession;
    }

    public void setAutoPass(String autoPass) {
        this.autoPass = autoPass;
    }

    public void setUsername(String userName) {
        this.userName = userName;
    }

    public int waitingCmdCount() {
        return cmdToSend.size();
    }

    public int cmdSendDelay() {
        return 2200;
    }

    public int currendCmdDelay() {
        if (lastDelayPackageMills != null) {

            int delay = (int) (2200 - ((new Date()).getTime() - lastDelayPackageMills.getTime()));
            return (Math.max(delay, 0));
        } else {
            return 0;
        }
    }

    public int totalCmdCount() {
        return queries.size() + cmdToSend.size();
    }

    private class Idle implements Runnable {

        private int replysPending;
        private Date authRetry;
        private boolean longDelay = false;

        public int getReplysPending() {
            return replysPending;
        }

        public void run() {
            Date now, idleThreadStartedOn = new Date();
            int replysPending = 0;

            do {
                now = new Date();

                //if (!aniDBAPIDown) authRetry = null;
                try {
                    if (!aniDBAPIDown && auth) {
                        replysPending = 0;
                        for (int i = 0; i < queries.size(); i++) {
                            if (queries.get(i).getSuccess() == null && queries.get(i).getSendOn() != null) {
                                if ((now.getTime() - queries.get(i).getSendOn().getTime()) > 15000) {
                                    if (queries.get(i).getRetries() < MAXRETRIES) {
                                        queries.get(i).setRetries(queries.get(i).getRetries() + 1);
                                        queries.get(i).setSendOn(null);
                                        Log(CommunicationEvent.EventType.Debug, "Cmd Timeout: Resend (Retries:" + queries.get(i).getRetries() + ")");
                                        queryCmd(queries.get(i).getCmd());
                                    } else {
                                        queries.get(i).setSuccess(false);
                                        Log(CommunicationEvent.EventType.Information, "Cmd", i, false);
                                        Log(CommunicationEvent.EventType.Error, "Sending command failed. (Retried " + MAXRETRIES + " times)");
                                    }
                                } else if (queries.get(i).getRetries() <= MAXRETRIES) {
                                    replysPending++;
                                }
                            }
                        }
                        this.replysPending = replysPending;

                        //quickfix
                        val idleForMoreThan5Mins = (now.getTime() - idleThreadStartedOn.getTime()) > 60000 * 5;
                        val lastReplyMoreThan1MinAgo = (lastReplyPackage == null || (now.getTime() - lastReplyPackage.getTime()) > 60000);
                        if (idleForMoreThan5Mins && lastReplyMoreThan1MinAgo &&
                                (lastDelayPackageMills == null || (now.getTime() - lastDelayPackageMills.getTime()) > 60000) &&
                                (authRetry == null && !longDelay && !cmdToSend.isEmpty())) {
                            authRetry = new Date(now.getTime() + 4 * 60 * 1000);
                            longDelay = true;
                            Log(CommunicationEvent.EventType.Warning, "Reply delay has passed 1 minute. Re-authentication in 5 min.");
                        }
                        val currentPingInterval = (long) pingBackOffInMillis * pingBackoffCount;
                        val needsPing =
                                (now.getTime() - idleThreadStartedOn.getTime()) > currentPingInterval
                                        && (now.getTime() - lastCommandSentAt.getTime()) > currentPingInterval && isAuthed;
                        if (needsPing && !pingQueued) {
                            // We are authenticated but nothing to do, so we ping sometimes
                            Log(CommunicationEvent.EventType.Debug, STR."Idle: Time for a ping, passed \{(now.getTime() - lastCommandSentAt.getTime())} ms since last command sent.");
                            pingQueued = true;
                            ping();
                            // TODO: Logout if we haven't done anything for about 30 minutes
                        }
                        if (longDelay && authRetry != null && (authRetry.getTime() - now.getTime() < 0)) {
                            idleThreadStartedOn = new Date();
                            authenticate();
                            longDelay = false;
                            authRetry = null;
                        }
                        //quickfix end
                    }
                } catch (Exception ignored) {
                }

                //if (aniDBAPIDown && authRetry == null) {
                //    authRetry = new Date(now.getTime() + 5 * 60 * 1000);
                //    Log(ComEvent.eType.Warning, "API down. Connection retry on " + Misc.DateToString(authRetry));
                //}
                //if (auth && aniDBAPIDown && authRetry != null && (authRetry.getTime() - now.getTime() < 0)) {
                //    authRetry = null;
                //    authenticate();
                //}

                try {
                    Thread.sleep(500);
                } catch (Exception ignored) {
                }
            } while (!shutdown);

            Log(CommunicationEvent.EventType.Debug, "Idle thread has shut down");
        }

        public void DelayedAuthentication() {
            longDelay = true;
            authRetry = new Date((new Date()).getTime() + 4 * 60 * 1000);
        }
    }

    private class Send implements Runnable {

        public void run() {
            byte[] cmdToSendBin;
            boolean cmdReordered;
            Date now;

            Log(CommunicationEvent.EventType.Debug, "Send: Entering send loop");
            while (!cmdToSend.isEmpty() && connected && !banned) {
                cmdReordered = false;
                now = new Date();

                synchronized (cmdToSend) {
                    Log(CommunicationEvent.EventType.Debug, "Send: replyHeadStart(" + replyHeadStart + ") isAuthed(" + isAuthed + ")");
                    if ((replyHeadStart < 5 && idleClass.getReplysPending() < 3) || !isAuthed) {

                        Log(CommunicationEvent.EventType.Debug, "Send: cmdLoginReq(" + cmdToSend.get(0).LoginReq() + ")");
                        if ((!cmdToSend.get(0).LoginReq() || isAuthed) &&
                                (NODELAY.contains(cmdToSend.get(0).Action()) || lastDelayPackageMills == null || (now.getTime() - lastDelayPackageMills.getTime()) > 2000)) {
                            //Cmd doesn't need login or client is logged in and is allowed to send
                            //send cmds from top to bottom

                            Log(CommunicationEvent.EventType.Debug, "Send: " + cmdToSend.get(0).Identifier() + " " + cmdToSend.get(0).Action());
                            try {
                                cmdToSendBin = TransformCmd(cmdToSend.get(0));

                                com.send(new DatagramPacket(cmdToSendBin, cmdToSendBin.length, aniDBIP, ANIDBAPIPORT));
                                lastCommandSentAt = new Date(now.getTime());

                                if (!NODELAY.contains(cmdToSend.get(0).Action()))
                                    lastDelayPackageMills = new Date(now.getTime());
                                replyHeadStart++;
                                cmdToSend.remove(0);

                            } catch (IOException e) {
                                Log(CommunicationEvent.EventType.Error, "Send: Error " + e.toString());
                            }
                            //Debug.Print("Send: " + cmdToSend[0].ToString(session).Replace("\n", " # "));


                        } else if (auth) {
                            //Cmd needs login but client is not connected OR Cmd needs delay which has not yet passed
                            //Move command without (login req./delay req.) to top
                            Log(CommunicationEvent.EventType.Debug, "Send: Try to reorder requests");
                            boolean needsLogin, otherCommandNeedsLogin, skipDelay, otherCommandSkipDelay, canOptimize;
                            needsLogin = cmdToSend.get(0).LoginReq();
                            skipDelay = NODELAY.contains(cmdToSend.get(0).Action());

                            if ((!isAuthed && needsLogin) || !skipDelay) {
                                for (int i = 0; i < cmdToSend.size(); i++) {
                                    otherCommandNeedsLogin = cmdToSend.get(i).LoginReq();
                                    otherCommandSkipDelay = NODELAY.contains(cmdToSend.get(i).Action());
                                    canOptimize = (!isAuthed && !skipDelay && !needsLogin && otherCommandSkipDelay && !otherCommandNeedsLogin) ||
                                            (!isAuthed && !skipDelay && needsLogin && !otherCommandNeedsLogin) ||
                                            (!isAuthed && skipDelay && needsLogin && !otherCommandNeedsLogin) ||
                                            (isAuthed && !skipDelay && !needsLogin && otherCommandSkipDelay) ||
                                            (isAuthed && !skipDelay && needsLogin && otherCommandSkipDelay);

                                    if (canOptimize) {
                                        Log(CommunicationEvent.EventType.Debug, "Send: cmdToSend reordered QueryId: " + i + " Action: " + cmdToSend.get(i).Action());
                                        cmdToSend.add(0, cmdToSend.get(i));
                                        cmdToSend.remove(i + 1);
                                        cmdReordered = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (!cmdReordered) {
                    try {
                        Thread.sleep(200);
                    } catch (Exception ignored) {
                    }
                }
            }
            Log(CommunicationEvent.EventType.Debug, "Send thread has shut down" + (banned ? " because you are BANNED" : ""));
            if (banned) {
                Terminate();
            }
        }

        private byte[] TransformCmd(Cmd cmd) {
            Query query;

            if (cmd.QueryId() == null) {
                cmd.QueryId(queries.size());
                query = new Query();
                query.setCmd(cmd);
                queries.add(query);
            } else {
                query = queries.get(cmd.QueryId());
            }
            query.setSendOn(new Date());

            Log(CommunicationEvent.EventType.Information, "Cmd", cmd.QueryId(), query.getRetries() == 0);

            if (isEncodingSet) {
                return cmd.toString(session).getBytes(StandardCharsets.UTF_8);

            } else {
                isEncodingSet = true;
                return cmd.toString(session).getBytes(StandardCharsets.US_ASCII);
            }
        }
    }

    private class Receive implements Runnable {

        private byte[] inflatePacket(ByteArrayInputStream stream) throws IOException {
            stream.skip(4);
            InflaterInputStream iis = new InflaterInputStream(stream, new Inflater(true));
            ByteArrayOutputStream baos = new ByteArrayOutputStream(2 * 1400);

            int readBytes;
            byte[] b = new byte[1024];
            while ((readBytes = iis.read(b)) != -1) baos.write(b, 0, readBytes);

            return baos.toByteArray();
        }

        public void run() {
            DatagramPacket packet;
            Thread reply;

            int length;
            byte[] replyBinary;
            byte[] packetBinary;
            while (connected) {
                try {
                    packet = new DatagramPacket(new byte[1400], 1400);
                    com.receive(packet);
                    aniDBAPIDown = false;
                    lastReplyPackage = new Date();

                    packetBinary = packet.getData();
                    if (packetBinary[0] == 0 && packetBinary[1] == 0) {
                        replyBinary = inflatePacket(new ByteArrayInputStream(packetBinary));
                        length = replyBinary.length;

                    } else {
                        replyBinary = packetBinary;
                        length = packet.getLength();
                    }

                    reply = new Thread(new Reply(new String(replyBinary, 0, length, "UTF8")));
                    reply.start();
                } catch (Exception e) {
                    Log(CommunicationEvent.EventType.Error, "Receive Error: " + e.toString());
                    //aniDBAPIDown = true;
                    connected = false;
                }//TODO
                replyHeadStart = 0;
            }
            Log(CommunicationEvent.EventType.Debug, "Receive thread has shut down");
        }

        private class Reply implements Runnable {

            String replyStr;

            public Reply(String replyStr) {
                this.replyStr = replyStr;
            }

            public void run() {
                try {
                    parseReply(replyStr);
                } catch (Exception e) {
                    Log(CommunicationEvent.EventType.Error, "Parse Error: " + e.toString());
                }
            }
        }
    }

    private void logOut(boolean sendCmd) {
        if (sendCmd && isAuthed) {
            Cmd cmd = new Cmd("LOGOUT", "logout", null, false);
            queryCmd(cmd);
            auth = false;
        } else {
            //Unexpected logout
            Log(CommunicationEvent.EventType.Debug, "Sync Logout");
            isEncodingSet = false;
            isAuthed = false;
            session = null;
        }
    }

    private void InternalMsgHandling(int queryIndex) {
        Reply reply = queries.get(queryIndex).getReply();

        if (reply.Identifier().equals("auth")) {
            switch (reply.ReplyId()) {
                case 200:
                case 201:
                    if (isAuthed) {
                        logOut(false);
                    }
                    session = reply.ReplyMsg().substring(0, reply.ReplyMsg().indexOf(" "));
                    reply.ReplyMsg(reply.ReplyMsg().substring(reply.ReplyMsg().indexOf(" ") + 1));
                    currentPort = Integer.parseInt(reply.ReplyMsg().split(":")[1].split(" ")[0]);
                    isAuthed = true;

                    aniDBAPIDown = false;

                    if (reply.ReplyId() == 201) ; //TODO Client Out of Date
                    break;

                case 500:
                    Log(CommunicationEvent.EventType.Error, "Wrong username and/or password. Try logging out of AniDB and back in.");
                    break;
                case 503:
                    Log(CommunicationEvent.EventType.Error, "Outdated Version");
                    break;
                case 504:
                    Log(CommunicationEvent.EventType.Error, "Client temporarily disabled by administrator - please try again later");
                    break;
            }

        } else if (reply.Identifier().equals("logout")) {
            isEncodingSet = false;
            isAuthed = false;
            session = null;

        } else if (reply.Identifier().equals("ping")) {
            pingQueued = false;
            val newPort = Integer.parseInt(reply.DataField().get(0));
            if (currentPort != newPort) {
                pingBackoffCount = Math.max(pingBackoffCount - 1, 1);
                Log(CommunicationEvent.EventType.Information, STR."Server port changed from \{currentPort} to \{newPort}. Decreasing backoff count to \{pingBackoffCount}");
            } else {
                pingBackoffCount = Math.min(pingBackoffCount + 1, MAX_PING_BACKOFF_COUNT);
                Log(CommunicationEvent.EventType.Information, STR."Server port is still \{currentPort}. Increasing backoff count to \{pingBackoffCount}");
            }

        } else if (reply.Identifier().equals("uptime")) {
        }

    }

    private void InternalMsgHandlingError(int queryIndex) {
        Reply reply = queryIndex < 0 ? serverReplies.get(~queryIndex) : queries.get(queryIndex).getReply();

        switch (reply.ReplyId()) {
            case 501:
            case 502:
            case 505:
            case 601:
            case 602:
            case 555:
                logOut(false);
                break;
        }

        switch (reply.ReplyId()) {
            case 501:
            case 506:
                if (auth) {
                    authenticate();
                }
                queryCmd(queries.get(queryIndex).getCmd());

                break;

            case 600:
            case 601:
            case 666:
                isAuthed = false;
                break;
            case 602:
                isAuthed = false;
                connected = false;
                Log(CommunicationEvent.EventType.Warning, "Server Busy. Re-authenticating in 5 min.");
                idleClass.DelayedAuthentication();

                //aniDBAPIDown = true;
                //Log(ComEvent.eType.Error, "Server API Failure Code: " + reply.ReplyId());
                //connected = false;//TODO
                break;

            case 555:
                banned = true;
            case 502:
            case 505:
            case 598:
                Log(CommunicationEvent.EventType.Error, "Client Failure Code: " + reply.ReplyId());
                break;
        }

    }

    // <editor-fold defaultstate="collapsed" desc="IModule">
    protected String modName = "UdpApi";
    protected eModState modState = eModState.New;

    public eModState ModState() {
        return modState;
    }

    public String ModuleName() {
        return modName;
    }

    public void Initialize(IAniAdd aniAdd, AniConfiguration configuration) {
        modState = eModState.Initialized;
    }

    public void Terminate() {
        modState = eModState.Terminating;

        shutdown = true;

        if (connected) {
            logOut();
            try {
                send.join(1000);
            } catch (InterruptedException ignored) {
            }
            com.close();
        }

        try {
            idle.join(1000);
        } catch (InterruptedException ignored) {
        }
        try {
            receive.join(1000);
        } catch (InterruptedException ignored) {
        }
        if (send.isAlive() || receive.isAlive() || idle.isAlive()) {
            Log(CommunicationEvent.EventType.Warning, "Thread abort timeout", idle.isAlive(), receive.isAlive(), send.isAlive());
        }

        if (com != null) {
            com.close();
        }

        modState = eModState.Terminated;
    }

    // </editor-fold>
}

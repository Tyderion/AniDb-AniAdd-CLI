package udpapi2;

import aniAdd.config.AniConfiguration;
import aniAdd.misc.ICallBack;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import udpapi2.command.CommandWrapper;
import udpapi2.command.FileCommand;
import udpapi2.command.LoginCommand;
import udpapi2.command.MylistAddCommand;
import udpapi2.query.Query;
import udpapi2.receive.Receive;
import udpapi2.reply.Reply;

import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class NewUdpApi implements AutoCloseable, Receive.Integration, Send.Integration, ParseReply.Integration {
    final Queue<CommandWrapper> commandQueue = new ConcurrentLinkedQueue<>();
    final Map<String, Query> queries = new ConcurrentHashMap<>();
    private DatagramSocket socket;
    private final ScheduledExecutorService executorService;
    private final int localPort;
    private InetAddress aniDbIp;
    private int aniDbPort;
    private boolean isInitialized = false;
    private boolean isLoggedIn = false;
    private String session = null;

    private Date lastSentDate = null;
    private boolean isSendScheduled = false;
    private boolean isLoginScheduled = false;
    private boolean shouldWaitLong = false;

    @Setter
    private String username;
    @Setter
    private String password;

    @Setter
    private ICallBack<Query> fileCommandCallback;
    @Setter
    private ICallBack<Query> mylistCommandCallback;


    public boolean Initialize(AniConfiguration configuration) {
        if (username == null || password == null) {
            Logger.getGlobal().severe("Username and password must be set");
            return false;
        }

        try {
            socket = new DatagramSocket(localPort);
            aniDbIp = InetAddress.getByName(configuration.getAnidbHost());
            aniDbPort = configuration.getAnidbPort();
        } catch (SocketException e) {
            Logger.getGlobal().severe(STR."Failed to create socket \{e.getMessage()}");
            return false;
        } catch (UnknownHostException e) {
            Logger.getGlobal().severe(STR."Failed to resolve host \{e.getMessage()}");
            return false;
        }
        executorService.execute(new Receive(this));
        isInitialized = true;
        return true;
    }


    public void queueCommand(CommandWrapper command) {
        commandQueue.add(command);
        if (!isSendScheduled && !isLoginScheduled) {
            scheduleNextCommand();
        }
    }

    private boolean queueLogin() {
        if (!isInitialized) {
            Logger.getGlobal().warning("Must be initialized before logging in");
            return false;
        }
        if (isSendScheduled) {
            Logger.getGlobal().warning("Command is scheduled, not scheduling login");
            return false;
        }
        if (isLoginScheduled) {
            Logger.getGlobal().warning("Login already scheduled, not scheduling login");
            return false;
        }
        try {
            val command = LoginCommand.Create(username, password);
            scheduleCommand(command, getNextSendDelay());
            isSendScheduled = true;
            isLoginScheduled = true;
            return true;
        } catch (IllegalArgumentException e) {
            Logger.getGlobal().warning(STR."Username or password is empty: \{e.getMessage()}");
            return false;
        }
    }

    private void scheduleNextCommand() {
        if (isSendScheduled) {
            return;
        }
        if (shouldWaitLong) {
            queueLogin();
            return;
        }
        val command = commandQueue.poll();
        if (command == null) {
            if (queries.isEmpty()) {
                QueryId.reset();
            }
            return;
        }
        if (command.getCommand().isNeedsLogin() && !isLoggedIn) {
            if (!isLoginScheduled) {
                queueLogin();
            }
            queueCommand(command);
            return;
        }
        scheduleCommand(command, getNextSendDelay());
    }

    private void scheduleCommand(@NotNull CommandWrapper command, Duration delay) {
        Logger.getGlobal().info(STR."Scheduling command \{command.toString()} in \{delay.toMillis()} ms");
        executorService.schedule(new Send(this, command, aniDbIp, aniDbPort), delay.toMillis(), TimeUnit.MILLISECONDS);
        isSendScheduled = true;
    }

    private Duration getNextSendDelay() {
        if (shouldWaitLong) {
            return UdpApiConfiguration.LONG_WAIT_TIME;
        }
        if (lastSentDate == null) {
            return Duration.ZERO;
        }
        val nextSend = UdpApiConfiguration.COMMAND_INTERVAL_MS.minus(Duration.ofMillis(new Date().getTime() - lastSentDate.getTime()));
        if (nextSend.isNegative()) {
            return Duration.ZERO;
        }
        return nextSend;
    }

    @Override
    public void close() throws Exception {
        if (socket != null) {
            socket.close();
            socket = null;
        }
        isInitialized = false;
    }

    @Override
    public void addReply(Reply reply) {
        val query = queries.get(reply.getFullTag());
        if (query == null) {
            Logger.getGlobal().warning(STR."Reply without corresponding query \{reply.toString()}");
            return;
        }
        query.setReply(reply);
        handleQueryReply(query);
    }

    private void handleQueryReply(Query query) {
        if (!query.success()) {
            Logger.getGlobal().warning(STR."Query failed: \{query.toString()}");
            handleQueryError(query);
            try {
                Thread.sleep(1000000);
            } catch (Exception _e) {

            }
            scheduleNextCommand();
            return;
        }
        queries.remove(query.getFullTag());

        if (query.getCommand() instanceof LoginCommand) {
            switch (query.getReply().getReplyStatus()) {
                case LOGIN_ACCEPTED, LOGIN_ACCEPTED_NEW_VERSION -> {
                    isLoggedIn = true;
                    session = query.getReply().getResponseData().getFirst();
                    Logger.getGlobal().info(STR."Logged in with session \{session}");
                    isLoginScheduled = false;
                    return;
                }
            }
        }

        if (query.getCommand() instanceof FileCommand) {
            switch (query.getReply().getReplyStatus()) {
                case NO_SUCH_FILE, FILE, MULTIPLE_FILES_FOUND -> {
                    if (fileCommandCallback != null) {
                        fileCommandCallback.invoke(query);
                        return;
                    }
                }
            }
        }

        if (query.getCommand() instanceof MylistAddCommand) {
            switch (query.getReply().getReplyStatus()) {
                case NO_SUCH_FILE, NO_SUCH_ANIME, NO_SUCH_GROUP, MYLIST_ENTRY_ADDED, FILE_ALREADY_IN_MYLIST,
                     MULTIPLE_FILES_FOUND, MYLIST_ENTRY_EDITED, NO_SUCH_MYLIST_ENTRY -> {
                    if (mylistCommandCallback != null) {
                        mylistCommandCallback.invoke(query);
                        return;
                    }
                }
            }
        }

        scheduleNextCommand();
        Logger.getGlobal().warning(STR."Unhandled query reply: \{query.toString()}");
    }

    private void handleQueryError(Query query) {
        if (query.getReply().isFatal()) {
            Logger.getGlobal().warning(STR."Fatal api error, waiting a long time: \{query.toString()}");
            disconnect();
        } else {
            Logger.getGlobal().warning(STR."Retrying query later: \{query.toString()}");
            queueCommand(query.getCommand());
        }
    }

    @Override
    public void onReceiveRawMessage(String message) {
        executorService.execute(new ParseReply(this, message));
    }

    @Override
    public boolean isSocketConnected() {
        return isInitialized;
    }

    @Override
    public void disconnect() {
        shouldWaitLong = true;
        isLoggedIn = false;
    }

    @Override
    public void receive(DatagramPacket packet) throws IOException {
        if (isInitialized) {
            socket.receive(packet);
        }
    }

    @Override
    public void sendPacket(DatagramPacket packet) throws IOException {
        if (isInitialized) {
            socket.send(packet);
        }
    }

    @Override
    public void addQuery(Query query) {
        if (queries.containsKey(query.getFullTag())) {
            queries.get(query.getFullTag()).setRetries(query.getRetries() + 1);
        } else {
            queries.put(query.getFullTag(), query);
        }
    }

    @Override
    public String getSession() {
        return session;
    }

    @Override
    public void onSent() {
        lastSentDate = new Date();
        isSendScheduled = false;
    }
}

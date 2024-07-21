package udpapi;

import aniAdd.config.AniConfiguration;
import aniAdd.misc.ICallBack;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import udpapi.command.*;
import udpapi.query.Query;
import udpapi.receive.Receive;
import udpapi.reply.Reply;
import udpapi.reply.ReplyStatus;

import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class UdpApi implements AutoCloseable, Receive.Integration, Send.Integration, ParseReply.Integration {
    final Queue<Command> commandQueue = new ConcurrentLinkedQueue<>();
    final Map<String, Query<?>> queries = new ConcurrentHashMap<>();
    final Map<Class<? extends Command>, IQueryCallback<?>> commandCallbacks = new ConcurrentHashMap<>();
    final Map<ReplyStatus, IReplyStatusCallback> replyStatusCallbacks = new ConcurrentHashMap<>();

    private static final Logger logger = Logger.getLogger(UdpApi.class.getName());

    private DatagramSocket socket;
    private final ScheduledExecutorService executorService;
    private final int localPort;
    private InetAddress aniDbIp;
    private int aniDbPort;
    private boolean isInitialized = false;
    private String session = null;

    private LoginStatus loginStatus = LoginStatus.LOGGED_OUT;
    private ScheduledFuture<?> logoutFuture;

    private Date lastSentDate = null;
    private boolean isSendScheduled = false;
    private boolean shouldWaitLong = false;

    private final String username;
    private final String password;
    private boolean shutdown;
    private Future<?> receiveFuture;
    private ICallBack<Void> onShutdownFinished;

    public <T extends Command> void registerCallback(Class<T> command, IQueryCallback<T> callback) {
        commandCallbacks.put(command, callback);
    }

    public <T extends Command> void registerCallback(ReplyStatus status, IReplyStatusCallback callback) {
        replyStatusCallbacks.put(status, callback);
    }

    public boolean Initialize(AniConfiguration configuration) {
        try {
            socket = new DatagramSocket(localPort);
            aniDbIp = InetAddress.getByName(configuration.getAnidbHost());
            aniDbPort = configuration.getAnidbPort();
        } catch (SocketException e) {
            logger.severe(STR."Failed to create socket \{e.getMessage()}");
            return false;
        } catch (UnknownHostException e) {
            logger.severe(STR."Failed to resolve host \{e.getMessage()}");
            return false;
        }
        receiveFuture = executorService.submit(new Receive(this));
        isInitialized = true;
        return true;
    }


    public void queueCommand(Command command) {
        commandQueue.add(command);
        if (!isSendScheduled && loginStatus != LoginStatus.LOGIN_PENDING) {
            scheduleNextCommand();
        }
    }

    private boolean queueLogin() {
        if (!isInitialized) {
            logger.warning("Must be initialized before logging in");
            return false;
        }
        if (isSendScheduled) {
            logger.warning("Command is scheduled, not scheduling login");
            return false;
        }
        if (loginStatus == LoginStatus.LOGIN_PENDING) {
            logger.warning("Login already scheduled, not scheduling login");
            return false;
        }
        if (loginStatus == LoginStatus.LOGGED_IN) {
            logger.warning("Already logged in, not scheduling login");
            return false;
        }
        try {
            val command = LoginCommand.Create(username, password);
            scheduleCommand(command, getNextSendDelay());
            isSendScheduled = true;
            loginStatus = LoginStatus.LOGIN_PENDING;
            return true;
        } catch (IllegalArgumentException e) {
            logger.warning(STR."Username or password is empty: \{e.getMessage()}");
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
            if (loginStatus == LoginStatus.LOGGED_IN) {
                queueLogout(false);
            }
            return;
        }
        if (logoutFuture != null) {
            logoutFuture.cancel(false);
            logoutFuture = null;
        }
        if (command.isNeedsLogin() && loginStatus != LoginStatus.LOGGED_IN) {
            if (loginStatus == LoginStatus.LOGGED_OUT) {
                queueLogin();
            }
            queueCommand(command);
            return;
        }
        scheduleCommand(command, getNextSendDelay());
    }

    private void scheduleCommand(@NotNull Command command, Duration delay) {
        logger.info(STR."Scheduling command \{command.toString()} in \{delay.toMillis()} ms");
        executorService.schedule(new Send<>(this, command, aniDbIp, aniDbPort), delay.toMillis(), TimeUnit.MILLISECONDS);
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
            logger.warning(STR."Reply without corresponding query \{reply.toString()}");
            return;
        }
        query.setReply(reply);
        handleQueryReply(query);
    }

    @SuppressWarnings("rawtypes")
    private void handleQueryReply(Query query) {
        if (replyStatusCallbacks.containsKey(query.getReply().getReplyStatus())) {
            replyStatusCallbacks.get(query.getReply().getReplyStatus()).invoke(query.getReply().getReplyStatus());
        }
        if (!query.success()) {
            logger.warning(STR."Query failed: \{query.toString()}");
            handleQueryError(query);
            return;
        }
        queries.remove(query.getFullTag());

        val command = query.getCommand();

        if (command instanceof LoginCommand) {
            switch (query.getReply().getReplyStatus()) {
                case LOGIN_ACCEPTED, LOGIN_ACCEPTED_NEW_VERSION -> {
                    session = query.getReply().getResponseData().getFirst();
                    logger.info(STR."Logged in with session \{session}");
                    loginStatus = LoginStatus.LOGGED_IN;
                }
            }
        } else if (command instanceof LogoutCommand) {
            switch (query.getReply().getReplyStatus()) {
                case LOGGED_OUT, NOT_LOGGED_IN -> {
                    session = null;
                    logger.info("Logged out");
                    loginStatus = LoginStatus.LOGGED_OUT;
                    if (shutdown) {
                        shutdown();
                        return;
                    }
                }
            }
        }
        if (commandCallbacks.containsKey(command.getClass())) {
            //noinspection unchecked
            commandCallbacks.get(command.getClass()).invoke(query);
        } else {
            // TODO: Make sure we only log that for relevant replies (i.e. not for login/logout queries)
            logger.warning(STR."Unhandled query reply: \{query.toString()}");
        }

        scheduleNextCommand();
    }

    private void handleQueryError(Query<?> query) {
        if (query.getReply().isFatal()) {
            logger.warning(STR."Fatal api error, waiting a long time: \{query.toString()}");
            disconnect();
        } else {
            logger.warning(STR."Retrying query later: \{query.toString()}");
            queueCommand(query.getCommand());
        }
    }

    private void queueLogout(boolean now) {
        if (loginStatus != LoginStatus.LOGGED_IN) {
            logger.warning("Not logged in, not logging out");
            return;
        }
        val delay = now ? Duration.ZERO : UdpApiConfiguration.LOGOUT_AFTER;
        logger.info(STR."Queuing logout at \{new Date().toInstant().plus(delay).atZone(ZoneId.systemDefault())}");
        if (logoutFuture != null) {
            logoutFuture.cancel(false);
        }
        logoutFuture = executorService.schedule(new Send<>(this, LogoutCommand.Create(!now), aniDbIp, aniDbPort), delay.toMillis(), TimeUnit.MILLISECONDS);
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
        loginStatus = LoginStatus.LOGGED_OUT;
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

    public void queueShutdown(ICallBack<Void> onShutdownFinished) {
        this.onShutdownFinished = onShutdownFinished;
        if (loginStatus == LoginStatus.LOGGED_OUT) {
            shutdown();
            return;
        }
        queueLogout(true);
        shutdown = true;
    }

    private void shutdown() {
        isInitialized = false;
        if (receiveFuture != null) {
            receiveFuture.cancel(true);
        }
        if (socket != null) {
            socket.close();
        }
        if (this.onShutdownFinished != null) {
            this.onShutdownFinished.invoke(null);
        }
    }

    private enum LoginStatus {
        LOGGED_OUT,
        LOGIN_PENDING,
        LOGGED_IN
    }

    public interface IQueryCallback<T extends Command> {
        void invoke(Query<T> query);
    }

    public interface IReplyStatusCallback {
        void invoke(udpapi.reply.ReplyStatus status);
    }
}

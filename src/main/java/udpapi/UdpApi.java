package udpapi;

import aniAdd.misc.ICallBack;
import config.CliConfiguration;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import udpapi.command.Command;
import udpapi.command.LoginCommand;
import udpapi.command.LogoutCommand;
import udpapi.query.Query;
import udpapi.receive.Receive;
import udpapi.reply.Reply;
import udpapi.reply.ReplyStatus;

import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class UdpApi implements AutoCloseable, Receive.Integration, Send.Integration, ParseReply.Integration {
    final Queue<Command> commandQueue = new ConcurrentLinkedQueue<>();
    final Map<String, Query<?>> queries = new ConcurrentHashMap<>();
    final Map<Class<? extends Command>, List<IQueryCallback<?>>> commandCallbacks = new ConcurrentHashMap<>();
    final Map<ReplyStatus, List<IReplyStatusCallback>> replyStatusCallbacks = new ConcurrentHashMap<>();
    private Command commandInFlight = null;

    private DatagramSocket socket;
    private final ScheduledExecutorService executorService;
    private final CliConfiguration.AniDbConfig aniDbConfig;
    private InetAddress aniDbIp;
    private boolean isInitialized = false;
    private String session = null;

    private LoginStatus loginStatus = LoginStatus.LOGGED_OUT;
    private ScheduledFuture<?> logoutFuture;

    private Date lastSentDate = null;
    private boolean isSendScheduled = false;
    private boolean shouldWaitLong = false;
    private boolean shutdown;
    private Future<?> receiveFuture;
    private ICallBack<Void> onShutdownFinished;
    private Future<?> requeueFuture;

    public UdpApi(ScheduledExecutorService executorService, CliConfiguration.AniDbConfig aniDbConfig) {
        this.executorService = executorService;
        this.aniDbConfig = aniDbConfig;
        Initialize();
    }

    public <T extends Command> void registerCallback(Class<T> command, IQueryCallback<T> callback) {
        if (commandCallbacks.containsKey(command)) {
            commandCallbacks.get(command).add(callback);
        } else {
            commandCallbacks.put(command, new ArrayList<>(List.of(callback)));
        }
    }

    public void registerCallback(ReplyStatus status, IReplyStatusCallback callback) {
        if (replyStatusCallbacks.containsKey(status)) {
            replyStatusCallbacks.get(status).add(callback);
        } else {
            replyStatusCallbacks.put(status, new ArrayList<>(List.of(callback)));
        }
    }

    private void Initialize() {
        registerCallback(LoginCommand.class, query -> {
            switch (query.getReply().getReplyStatus()) {
                case LOGIN_ACCEPTED, LOGIN_ACCEPTED_NEW_VERSION -> {
                    session = query.getReply().getResponseData().getFirst();
                    log.info("Successfully logged in");
                    loginStatus =  LoginStatus.LOGGED_IN;
                    shouldWaitLong = false;
                }
            }
        });
        registerCallback(LogoutCommand.class, query -> {
            switch (query.getReply().getReplyStatus()) {
                case LOGGED_OUT, NOT_LOGGED_IN -> {
                    session = null;
                    log.info("Logged out");
                    loginStatus = LoginStatus.LOGGED_OUT;
                    if (shutdown) {
                        shutdown();
                    }
                }
            }
        });

        try {
            socket = new DatagramSocket(aniDbConfig.localPort());
            aniDbIp = InetAddress.getByName(aniDbConfig.host());
        } catch (SocketException e) {
            log.error(STR."Failed to create socket \{e.getMessage()}");
            return;
        } catch (UnknownHostException e) {
            log.error(STR."Failed to resolve host \{e.getMessage()}");
            return;
        }
        receiveFuture = executorService.submit(new Receive(this));
        isInitialized = true;
    }


    public void queueCommand(Command command) {
        commandQueue.add(command);
        if (!isSendScheduled && loginStatus != LoginStatus.LOGIN_PENDING) {
            scheduleNextCommand();
        }
    }

    @Synchronized
    private void setCommandInFlight(Command command) {
        if (command instanceof  LoginCommand || command instanceof LogoutCommand) {
            // We don't care about login/logout, those don't need to be rescheduled
            return;
        }
        commandInFlight = command;
    }

    @Synchronized
    private Command getCommandInFlight() {
        return commandInFlight;
    }

    private boolean queueLogin() {
        if (!isInitialized) {
            log.warn("Must be initialized before logging in");
            return false;
        }
        if (isSendScheduled) {
            log.warn("Command is scheduled, not scheduling login");
            return false;
        }
        if (loginStatus == LoginStatus.LOGIN_PENDING) {
            log.warn("Login already scheduled, not scheduling login");
            return false;
        }
        if (loginStatus == LoginStatus.LOGGED_IN) {
            log.warn("Already logged in, not scheduling login");
            return false;
        }
        try {
            val command = LoginCommand.Create(aniDbConfig.username(), aniDbConfig.password());
            scheduleCommand(command, getNextSendDelay());
            isSendScheduled = true;
            loginStatus = LoginStatus.LOGIN_PENDING;
            return true;
        } catch (IllegalArgumentException e) {
            log.warn(STR."Username or password is empty: \{e.getMessage()}");
            return false;
        }
    }

    private void scheduleNextCommand() {
        if (shutdown) {
            log.info("Shutdown scheduled, not scheduling next command");
            return;
        }
        if (getCommandInFlight() != null) {
            log.debug("Command in flight, not scheduling next command");
            return;
        }
        if (isSendScheduled) {
            log.trace("Send scheduled, not scheduling next command");
            return;
        }
        if (shouldWaitLong) {
            log.trace("Should wait long, not scheduling next command");
            queueLogin();
            return;
        }
        log.trace(STR."\{commandQueue.size()} commands in queue. Scheduled next command");
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
            log.trace("Command needs login, not logged in, queueing login and command");
            if (loginStatus == LoginStatus.LOGGED_OUT) {
                queueLogin();
            }
            queueCommand(command);
            return;
        }
        scheduleCommand(command, getNextSendDelay());
    }

    private void scheduleCommand(@NotNull Command command, Duration delay) {
        log.info(STR."Scheduling command \{command.toString()} in \{delay.toMillis()}ms at \{formatDelay(delay)}");
        executorService.schedule(new Send<>(this, command, aniDbIp, aniDbConfig.port()), delay.toMillis(), TimeUnit.MILLISECONDS);
        requeueFuture = executorService.schedule(() -> {
            log.info(STR."Did not receive a response for \{command.toString()} in \{UdpApiConfiguration.MAX_RESPONSE_WAIT_TIME.toSeconds()}s. Assuming it was lost in transit. Rescheduling command and sending next one.");
            rescheduleCommandInFlight();
        }, delay.plus(UdpApiConfiguration.MAX_RESPONSE_WAIT_TIME).toMillis(), TimeUnit.MILLISECONDS);
        isSendScheduled = true;
        setCommandInFlight(command);
    }

    private Duration getNextSendDelay() {
        if (shouldWaitLong) {
            return UdpApiConfiguration.LONG_WAIT_TIME;
        }
        if (lastSentDate == null) {
            return Duration.ZERO;
        }
        val nextSend = UdpApiConfiguration.COMMAND_INTERVAL.minus(Duration.ofMillis(new Date().getTime() - lastSentDate.getTime()));
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
        if (replyStatusCallbacks.containsKey(reply.getReplyStatus())) {
            replyStatusCallbacks.get(reply.getReplyStatus()).forEach(cb -> cb.invoke(reply.getReplyStatus()));
        }
        if (reply.getReplyStatus().isFatal()) {
            disconnect();
            log.warn(STR."Fatal reply: \{reply.toString()}, will wait for a \{UdpApiConfiguration.LONG_WAIT_TIME.toMinutes()} minutes until \{formatDelay(UdpApiConfiguration.LONG_WAIT_TIME)} before trying again.");
            rescheduleCommandInFlight();
            scheduleNextCommand();
            return;
        }
        val query = queries.get(reply.getFullTag());
        if (query == null) {
            log.warn(STR."Reply without corresponding query \{reply.toString()}");
            rescheduleCommandInFlight();
            return;
        }
        query.setReply(reply);
        handleQueryReply(query);
    }

    private void rescheduleCommandInFlight() {
        val command = getCommandInFlight();
        setCommandInFlight(null);
        if (command == null || command instanceof  LoginCommand || command instanceof LogoutCommand) {
            return;
        }
        log.info(STR."Rescheduling command: \{command}");
        commandQueue.add(command);
        scheduleNextCommand();
    }

    @SuppressWarnings("rawtypes")
    private void handleQueryReply(Query query) {
        // Only one command is currently in flight at a time, so if we receive a response, we can safely set the command in flight to null
        setCommandInFlight(null);
        if (!query.success()) {
            log.warn(STR."Query failed: \{query.toString()}");
            handleQueryError(query);
            return;
        }
        queries.remove(query.getFullTag());

        val command = query.getCommand();
        if (commandCallbacks.containsKey(command.getClass())) {
            commandCallbacks.get(command.getClass()).forEach(cb -> cb.invoke(query));
        } else {
            log.warn(STR."Unhandled query reply: \{query.toString()}");
        }

        scheduleNextCommand();
    }

    private void handleQueryError(Query<?> query) {
        if (query.getReply().isFatal()) {
            log.warn(STR."Fatal api error, waiting a long time: \{query.toString()}");
            disconnect();
        } else {
            // TODO: Handle with reply status callbacks (improve those)
            if (query.getReply().getReplyStatus() == ReplyStatus.LOGIN_FIRST) {
                loginStatus = LoginStatus.LOGGED_OUT;
                log.info("Command Failed, not logged in, setting login status to LOGGED_OUT");
                if (query.getCommand() instanceof LogoutCommand) {
                    // We tried to logout, but we were not logged in, so we can safely ignore this
                    log.info("Logout failed due to not being logged in.");
                    return;
                }
            }
            log.warn(STR."Retrying query later: \{query.toString()}");
            queueCommand(query.getCommand());
        }
    }

    private void queueLogout(boolean now) {
        if (loginStatus != LoginStatus.LOGGED_IN) {
            log.warn("Not logged in, not logging out");
            return;
        }
        val delay = now ? Duration.ZERO : UdpApiConfiguration.LOGOUT_AFTER;
        log.info(STR."Queuing logout in \{delay.toMillis()}ms at \{formatDelay(delay)}");
        if (logoutFuture != null) {
            logoutFuture.cancel(false);
        }
        logoutFuture = executorService.schedule(new Send<>(this, LogoutCommand.Create(!now), aniDbIp, aniDbConfig.port()), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void onReceiveRawMessage(String message) {
        log.info(STR."Received message: \{message}, cancelling check for lost command.");
        if (requeueFuture != null) {
            requeueFuture.cancel(false);
        }
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
        if (loginStatus != LoginStatus.LOGGED_IN) {
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

    private String formatDelay(Duration delay) {
        val date = new Date().toInstant().plus(delay).atZone(ZoneId.systemDefault());
        return date.format(DateTimeFormatter.ISO_DATE_TIME);
    }
}

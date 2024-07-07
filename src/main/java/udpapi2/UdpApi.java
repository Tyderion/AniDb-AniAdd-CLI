package udpapi2;

import aniAdd.IAniAdd;
import aniAdd.Modules.BaseModule;
import aniAdd.config.AniConfiguration;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import lombok.val;
import udpapi2.command.CommandWrapper;
import udpapi2.command.LoginCommand;
import udpapi2.command.LogoutCommand;
import udpapi2.query.Query;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UdpApi extends BaseModule {
    public static final int PROTOCOL_VERSION = 3;
    public static final String CLIENT_TAG = "AniAddCLI";
    public static final int CLIENT_VERSION = 4;
    private static final int LOCAL_PORT = 3333;
    private static final int DELAY = 2200;
    private static final int EXECUTOR_THREADS = 10;

    private InetAddress aniDbIp;

    private DatagramSocket socket;
    private Date lastSent;

    private eModState modState = eModState.New;
    private AniConfiguration configuration;
    private ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(EXECUTOR_THREADS);

    @Getter
    @Setter
    private boolean connectedToSocket;

    @Getter
    @Setter
    private boolean isLoggedIn;
    @Getter
    @Setter
    private String session;
    @Setter
    private String username;
    @Setter
    private String password;

    @Getter
    @Setter
    private boolean banned;

    private final List<CommandWrapper> commandQueue = new ArrayList<>();
    private Map<String, Query> queries = new HashMap<>();

    private Thread receiveThread = new Thread(new Receive(this));
    private Thread idleThread = new Thread(new Idle(this));

    private boolean isSendScheduled = false;

    @Override
    public String ModuleName() {
        return "UdpApi";
    }

    @Override
    public eModState ModState() {
        return modState;
    }

    @Override
    public void Initialize(IAniAdd aniAdd, AniConfiguration configuration) {
        this.configuration = configuration;
        modState = eModState.Initializing;

        if (connectToSocket()) {
            if (executor.isTerminated() || executor.isShutdown()) {
                executor = new ScheduledThreadPoolExecutor(EXECUTOR_THREADS);
            }
            modState = eModState.Initialized;
        } else {
            Terminate();
        }
    }

    public synchronized void receivePacket(DatagramPacket p) throws IOException {
        socket.receive(p);
    }

    public synchronized void sendPacket(DatagramPacket p) throws IOException {
        socket.send(p);
    }

    public void scheduleParseReply(String message) {
        executor.execute(new ParseReply(this, message));
    }

    @Override
    public void Terminate() {
        modState = eModState.Terminating;
        executor.shutdown();

        if (isLoggedIn && connectedToSocket) {
            logOut(true);
        }

        if (socket != null) {
            socket.close();
        }
    }

    public boolean hasCommandsToSend() {
        return !commandQueue.isEmpty();
    }

    private boolean logIn() {
        try {
            val command = LoginCommand.Create(username, password);
            queueCommand(command);
            return true;
        } catch (IllegalArgumentException e) {
            Log(CommunicationEvent.EventType.Error, "Username or password is empty", e.getMessage());
            return false;
        }
    }

    private void logOut(boolean sendCommand) {
        if (sendCommand) {
            queueCommand(LogoutCommand.Create());
        } else {
            Log(CommunicationEvent.EventType.Debug, "Unexpected Logout");
            isLoggedIn = false;
            session = null;
        }
    }

    public synchronized void addQuery(Query query) {
        queries.put(query.getTag(), query);
    }

    public synchronized CommandWrapper getNextCommand() {
        if (commandQueue.isEmpty()) {
            Logger.getGlobal().log(Level.INFO, "No commands in queue");
            return null;
        }
        if (isLoggedIn && isConnectedToSocket()) {
            return commandQueue.removeFirst();
        }
        for (int i = 0; i < commandQueue.size(); i++) {
            // Find first non-auth command
            if (!commandQueue.get(i).getCommand().isNeedsLogin()) {
                return commandQueue.remove(i);
            }
        }
        logIn();
        return getNextCommand();
    }

    private long getNextSendDelay() {
        if (lastSent == null) {
            return 0;
        }
        return Math.max(DELAY - (new Date().getTime() - lastSent.getTime()), 0);
    }

    public synchronized void queueCommand(CommandWrapper command) {
        connectToSocket();
        commandQueue.add(command);
        Log(CommunicationEvent.EventType.Debug, STR."Added \{command.getCommand().getAction()} cmd to queue");

        scheduleSend();
        startReceiving();
    }

    private synchronized void scheduleSend() {
        if (isSendScheduled) {
            return;
        }
        val nextCommand = getNextCommand();
        if (nextCommand == null) {
            return;
        }
        executor.schedule(
                new Send(this, nextCommand, aniDbIp, configuration.getAnidbPort()),
                getNextSendDelay(),
                java.util.concurrent.TimeUnit.MILLISECONDS);
        isSendScheduled = true;
    }


    public synchronized void commandSent() {
        lastSent = new Date();
        isSendScheduled = false;
        scheduleSend();
    }

    private void startReceiving() {
        if (receiveThread.getState() == java.lang.Thread.State.NEW) {
            Log(CommunicationEvent.EventType.Debug, "Starting Receive Thread");
            receiveThread.start();
        } else if (receiveThread.getState() == java.lang.Thread.State.TERMINATED) {
            Log(CommunicationEvent.EventType.Debug, "Restarting Receive thread");
            receiveThread = new Thread(new Receive(this));
            receiveThread.start();
        }
    }

    private boolean connectToSocket() {
        if (connectedToSocket) {
            return true;
        }
        try {
            aniDbIp = java.net.InetAddress.getByName(configuration.getAnidbHost());
            socket = new java.net.DatagramSocket(LOCAL_PORT);
            connectedToSocket = true;
            return true;
        } catch (Exception e) {
            Log(CommunicationEvent.EventType.Error, "Couldn't open socket. (Client may be running twice)");
            return false;
        }
    }

    public void disconnect() {
        connectedToSocket = false;
        isLoggedIn = false;
        session = null;
        if (socket != null) {
            socket.close();
        }
    }
}

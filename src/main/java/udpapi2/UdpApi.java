package udpapi2;

import aniAdd.IAniAdd;
import aniAdd.Modules.BaseModule;
import aniAdd.config.AniConfiguration;
import lombok.Setter;
import lombok.val;
import udpApi.Query;
import udpapi2.command.CommandWrapper;
import udpapi2.command.LoginCommand;
import udpapi2.command.LogoutCommand;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UdpApi extends BaseModule {
    public static final int PROTOCOL_VERSION = 3;
    public static final String CLIENT_TAG = "AniAddCLI";
    public static final int CLIENT_VERSION = 4;
    private static final int LOCAL_PORT = 3333;

    private InetAddress aniDbIp;
    private DatagramSocket socket;

    private eModState modState = eModState.New;
    private AniConfiguration configuration;
    private boolean connectedToSocket;
    private boolean isLoggedIn;
    private String session;
    @Setter
    private String username;
    @Setter
    private String password;

    private final List<CommandWrapper> commandQueue = new ArrayList<>();
    private Map<String, Query> queries = new HashMap<>();

    private Thread sendThread = new Thread(new Send());
    private Thread receiveThread = new Thread(new Receive());
    private Thread idleThread = new Thread(new Idle());

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
            modState = eModState.Initialized;
        } else {
            Terminate();
        }
    }

    @Override
    public void Terminate() {
        modState = eModState.Terminating;

        if (isLoggedIn && connectedToSocket) {
            logOut(true);
            try {
                sendThread.join(1000);
            } catch (InterruptedException ignored) {
            }
        }

        if (socket != null) {
            socket.close();
        }
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

    private void queueCommand(CommandWrapper command) {
        connectToSocket();
        synchronized (commandQueue) {
            commandQueue.add(command);
            Log(CommunicationEvent.EventType.Debug, STR."Added \{command.getCommand().getAction()} cmd to queue");
        }

        startSending();
        startReceiving();
    }

    private void startSending() {
        if (sendThread.getState() == Thread.State.NEW) {
            Log(CommunicationEvent.EventType.Debug, "Starting Send thread");
            sendThread.start();
        } else if (sendThread.getState() == Thread.State.TERMINATED) {
            Log(CommunicationEvent.EventType.Debug, "Restarting Send thread");
            sendThread = new Thread(new Send());
            sendThread.start();
        }
    }

    private void startReceiving() {
        if (receiveThread.getState() == java.lang.Thread.State.NEW) {
            Log(CommunicationEvent.EventType.Debug, "Starting Receive Thread");
            receiveThread.start();
        } else if (receiveThread.getState() == java.lang.Thread.State.TERMINATED) {
            Log(CommunicationEvent.EventType.Debug, "Restarting Receive thread");
            receiveThread = new Thread(new Receive());
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

    private class Send implements Runnable {

        @Override
        public void run() {

        }
    }

    private class Receive implements Runnable {

        @Override
        public void run() {

        }
    }

    private class Idle implements Runnable {

        @Override
        public void run() {

        }
    }
}

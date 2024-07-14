package udpapi2;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.jetbrains.annotations.Nullable;
import udpapi2.command.Command;
import udpapi2.query.Query;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
class Send implements Runnable {

    final Integration integration;
    @NonNull
    final Command commandToSend;
    private final InetAddress aniDbIp;
    private final int port;


    @Override
    public void run() {
        Logger.getGlobal().log(Level.INFO, STR."Send command \{commandToSend.getIdentifier()}");
        val query = createQuery(commandToSend);
        integration.addQuery(query);
        try {
            integration.sendPacket(query.getBytes(integration.getSession(), aniDbIp, port));
        } catch (IOException e) {
            Logger.getGlobal().log(Level.SEVERE, "Failed to send packet", e);
        }
        integration.onSent();
    }

    private Query createQuery(Command command) {
        return new Query(command, new Date());
    }

    public interface Integration {
        void sendPacket(DatagramPacket packet) throws IOException;

        void addQuery(Query query);

        @Nullable
        String getSession();

        void onSent();
    }
}
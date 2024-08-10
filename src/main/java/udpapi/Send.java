package udpapi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.Nullable;
import udpapi.command.Command;
import udpapi.query.Query;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Date;

@Slf4j
@RequiredArgsConstructor
class Send<T extends Command> implements Runnable {

    final Integration integration;
    @NonNull
    final T commandToSend;
    private final InetAddress aniDbIp;
    private final int port;


    @Override
    public void run() {
        log.debug( STR."Send command \{commandToSend.getIdentifier()}");
        val query = createQuery(commandToSend);
        integration.addQuery(query);
        try {
            integration.sendPacket(query.getBytes(integration.getSession(), aniDbIp, port));
        } catch (IOException e) {
            log.error(STR."Failed to send packet \{e.getMessage()}");
        }
        integration.onSent();
    }

    private Query<T> createQuery(T command) {
        return new Query<T>(command, new Date());
    }

    public interface Integration {
        void sendPacket(DatagramPacket packet) throws IOException;

        void addQuery(Query query);

        @Nullable
        String getSession();

        void onSent();
    }
}
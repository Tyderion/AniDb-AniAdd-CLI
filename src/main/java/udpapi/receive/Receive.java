package udpapi.receive;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

@Log
@RequiredArgsConstructor
public class Receive implements Runnable {

    @NotNull
    private final Integration integration;

    @Override
    public void run() {
        log.fine("Receive thread started");
        while (integration.isSocketConnected()) {
            try {
                val packet = new DatagramPacket(new byte[1400], 1400);
                integration.receive(packet);

                byte[] replyBinary;
                int length;
                val packetBinary = packet.getData();
                if (packetBinary[0] == 0 && packetBinary[1] == 0) {
                    replyBinary = inflatePacket(new ByteArrayInputStream(packetBinary));
                    length = replyBinary.length;
                } else {
                    replyBinary = packetBinary;
                    length = packet.getLength();
                }
                integration.onReceiveRawMessage(new String(replyBinary, 0, length, StandardCharsets.UTF_8));

            } catch (SocketException e) {
                log.fine("Socket was closed");
                integration.disconnect();
            } catch (Exception e) {
                log.severe(STR."Receive Error: \{e.getMessage()}");
                integration.disconnect();
            }
        }

        log.fine( "Receive thread stopped");
    }

    private byte[] inflatePacket(ByteArrayInputStream stream) throws IOException {
        stream.skip(4);
        InflaterInputStream iis = new InflaterInputStream(stream, new Inflater(true));
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2 * 1400);

        int readBytes;
        byte[] b = new byte[1024];
        while ((readBytes = iis.read(b)) != -1) baos.write(b, 0, readBytes);

        return baos.toByteArray();
    }

    public interface Integration {
        void onReceiveRawMessage(String message);

        boolean isSocketConnected();

        void disconnect();

        void receive(DatagramPacket packet) throws IOException;
    }
}
package udpapi2;

import aniAdd.Communication;
import lombok.RequiredArgsConstructor;
import lombok.val;
import udpApi.Mod_UdpApi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

@RequiredArgsConstructor
class Receive implements Runnable {

    final UdpApi api;

    @Override
    public void run() {
        while (api.isConnectedToSocket()) {
            try {
                val packet = new DatagramPacket(new byte[1400], 1400);
                api.receivePacket(packet);

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

                api.scheduleParseReply(new String(replyBinary, 0, length, "UTF8"));

            } catch (Exception e) {
                Logger.getGlobal().log(Level.SEVERE, STR."Receive Error: \{e.getMessage()}");
                api.disconnect();
            }
        }

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
}
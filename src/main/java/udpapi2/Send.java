package udpapi2;

import aniAdd.config.AniConfiguration;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import udpapi2.command.CommandWrapper;
import udpapi2.query.Query;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
class Send implements Runnable {

    final UdpApi api;
    @NonNull
    final CommandWrapper commandToSend;
    private final InetAddress aniDbIp;
    private final int port;


    @Override
    public void run() {
        Logger.getGlobal().log(Level.INFO, "Send thread started");
        val query = createQuery(commandToSend);
        api.addQuery(query);
        try {
            api.sendPacket(query.getBytes(api.getSession(), aniDbIp, port));
        } catch (IOException e) {
            Logger.getGlobal().log(Level.SEVERE, "Failed to send packet", e);
        }
        api.commandSent();
    }

    private Query createQuery(CommandWrapper command) {
        return new Query(command, new Date());
    }
}
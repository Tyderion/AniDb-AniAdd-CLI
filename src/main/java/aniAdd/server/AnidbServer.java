package aniAdd.server;

import aniAdd.IAniAdd;
import com.sun.net.httpserver.HttpServer;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@RequiredArgsConstructor
public class AnidbServer {
    private final int port;
    private final IAniAdd aniAdd;
    private HttpServer server;


    public AnidbServer init() throws IOException {
        val executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/markWatched", new MarkWatchedHandler(aniAdd));
        server.setExecutor(executor);
        return this;
    }

    public void start() {
        server.start();
    }
}

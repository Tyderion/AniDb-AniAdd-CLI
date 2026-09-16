import com.google.gson.Gson;
import com.google.gson.JsonObject;
import config.blocks.FileConfig;
import config.blocks.KodiConfig;
import config.blocks.KodiLibraryScanConfig;
import kodi.library.KodiLibraryScanner;
import lombok.val;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import processing.FileInfo;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

public class KodiLibraryScannerTest {
    private final Gson gson = new Gson();

    @TempDir
    Path shows;

    /** Answers like Kodi 21: Files.GetSources, then VideoLibrary.Scan with "OK" and an OnScanFinished shortly after. */
    private class FakeKodi extends WebSocketServer {
        final CountDownLatch started = new CountDownLatch(1);
        final List<String> log = new CopyOnWriteArrayList<>();
        final List<String> scannedDirectories = new CopyOnWriteArrayList<>();
        private final java.util.concurrent.ScheduledExecutorService delay = Executors.newSingleThreadScheduledExecutor();

        FakeKodi() {
            super(new InetSocketAddress("127.0.0.1", 0));
            setReuseAddr(true);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            val request = gson.fromJson(message, JsonObject.class);
            val id = request.get("id").getAsInt();
            switch (request.get("method").getAsString()) {
                case "Files.GetSources" -> conn.send(STR."""
                        {"id":\{id},"jsonrpc":"2.0","result":{"limits":{"end":2,"start":0,"total":2},"sources":[
                          {"file":"/storage/media/anime/movies/","label":"Anime Movies"},
                          {"file":"/storage/media/anime/series/","label":"anime series"}]}}""");
                case "VideoLibrary.Scan" -> {
                    val directory = request.getAsJsonObject("params").get("directory").getAsString();
                    log.add(STR."scan \{directory}");
                    scannedDirectories.add(directory);
                    conn.send(STR."{\"id\":\{id},\"jsonrpc\":\"2.0\",\"result\":\"OK\"}");
                    delay.schedule(() -> {
                        log.add(STR."finished \{directory}");
                        conn.send("{\"jsonrpc\":\"2.0\",\"method\":\"VideoLibrary.OnScanFinished\",\"params\":{\"data\":null,\"sender\":\"xbmc\"}}");
                    }, 300, TimeUnit.MILLISECONDS);
                }
                default -> conn.send(STR."{\"id\":\{id},\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32601,\"message\":\"Method not found.\"}}");
            }
        }

        @Override
        public void onStart() {
            started.countDown();
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
        }

        void shutdown() throws InterruptedException {
            delay.shutdownNow();
            stop();
        }
    }

    @Test
    public void resolvesNamesAndScansChangedFoldersOneAfterAnother() throws Exception {
        val kodi = new FakeKodi();
        kodi.start();
        try {
            assertThat(kodi.started.await(10, TimeUnit.SECONDS), is(true));
            val scanner = new KodiLibraryScanner(() -> kodiConfig(kodi.getPort(), KodiLibraryScanConfig.Library.builder().name("Anime Series").localPath(shows).build()));

            scanner.onFileFinished(changedFile(shows.resolve("Frieren").resolve("e01.mkv")));
            scanner.onFileFinished(changedFile(shows.resolve("Frieren").resolve("e02.mkv")));
            scanner.onFileFinished(changedFile(shows.resolve("JoJo").resolve("e01.mkv")));
            val unchanged = fileInfo(shows.resolve("Akira").resolve("e01.mkv"));
            scanner.onFileFinished(unchanged);

            scanner.onBatchDone().get(30, TimeUnit.SECONDS);

            assertThat(kodi.scannedDirectories.stream().sorted().toList(), contains(
                    "/storage/media/anime/series/Frieren/", "/storage/media/anime/series/JoJo/"));
            // The second scan may only start once Kodi reported the first one finished.
            assertThat(kodi.log.get(1), is(kodi.log.get(0).replace("scan", "finished")));

            // The batch was drained, so the next one has nothing to do.
            scanner.onBatchDone().get(30, TimeUnit.SECONDS);
            assertThat(kodi.scannedDirectories.size(), is(2));
        } finally {
            kodi.shutdown();
        }
    }

    @Test
    public void keepsTheChangesForTheNextBatchWhenKodiIsUnreachable() throws Exception {
        // Nothing listens on port 1 at first: the batch must complete normally and hand its files to the next batch.
        val port = new AtomicInteger(1);
        val library = KodiLibraryScanConfig.Library.builder().path("/storage/media/anime/series").build();
        val scanner = new KodiLibraryScanner(() -> kodiConfig(port.get(), library));
        scanner.onFileFinished(changedFile(shows.resolve("Frieren").resolve("e01.mkv")));

        scanner.onBatchDone().get(30, TimeUnit.SECONDS);

        val kodi = new FakeKodi();
        kodi.start();
        try {
            assertThat(kodi.started.await(10, TimeUnit.SECONDS), is(true));
            port.set(kodi.getPort());
            scanner.onBatchDone().get(30, TimeUnit.SECONDS);
            // No localPath, so the library is scanned whole, with the trailing slash kodi's source carries.
            assertThat(kodi.scannedDirectories, contains("/storage/media/anime/series/"));
        } finally {
            kodi.shutdown();
        }
    }

    private static KodiConfig kodiConfig(int port, KodiLibraryScanConfig.Library library) {
        return KodiConfig.builder()
                .host("127.0.0.1")
                .port(port)
                .libraryScan(KodiLibraryScanConfig.builder()
                        .enabled(true)
                        .timeoutInMinutes(1)
                        .libraries(List.of(library))
                        .build())
                .build();
    }

    private static FileInfo changedFile(Path path) throws Exception {
        val fileInfo = fileInfo(path);
        fileInfo.setLibraryChanged(true);
        return fileInfo;
    }

    private static FileInfo fileInfo(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
        return new FileInfo(path.toFile(), path.hashCode(), null, FileConfig.builder().build());
    }
}

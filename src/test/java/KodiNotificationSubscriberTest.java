import aniAdd.kodi.KodiNotificationSubscriber;
import lombok.val;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Regression test for the crash where a forcefully dropped kodi connection took the whole process with it:
 * onError called System.exit(1), and onClose called connect() from the websocket thread, which the library
 * rejects outright ("WebSocketClient objects are not reuseable").
 */
public class KodiNotificationSubscriberTest {

    /** Longer than the subscriber's 5s initial reconnect delay, short enough to fail fast if it never comes back. */
    private static final int RECONNECT_TIMEOUT_SECONDS = 30;

    @Test
    public void reconnectsAfterTheConnectionIsForcefullyDropped() throws Exception {
        val started = new CountDownLatch(1);
        val firstConnection = new CountDownLatch(1);
        val secondConnection = new CountDownLatch(2);
        val lastConnection = new AtomicReference<WebSocket>();

        val server = new WebSocketServer(new InetSocketAddress("127.0.0.1", 0)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                lastConnection.set(conn);
                firstConnection.countDown();
                secondConnection.countDown();
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
            }

            @Override
            public void onStart() {
                started.countDown();
            }
        };
        server.setReuseAddr(true);
        server.start();

        KodiNotificationSubscriber subscriber = null;
        try {
            assertThat("the test websocket server never bound a port", started.await(10, TimeUnit.SECONDS), is(true));
            subscriber = new KodiNotificationSubscriber(
                    new URI(STR."ws://127.0.0.1:\{server.getPort()}/jsonrpc"), null, null, null);
            subscriber.start();

            assertThat("never connected in the first place", firstConnection.await(10, TimeUnit.SECONDS), is(true));

            // Close without a websocket close handshake — what a killed kodi or a yanked network looks like.
            lastConnection.get().closeConnection(1006, "forcefully dropped");

            assertThat("did not reconnect after the connection was dropped",
                    secondConnection.await(RECONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
        } finally {
            if (subscriber != null) {
                subscriber.shutdown();
            }
            server.stop();
        }
    }

    @Test
    public void survivesAConnectionThatIsRefusedOutright() throws Exception {
        // Nothing is listening on port 1, so the very first attempt fails. The old onError killed the JVM here,
        // which is why reaching the assertion at all is most of the point.
        val subscriber = new KodiNotificationSubscriber(new URI("ws://127.0.0.1:1/jsonrpc"), null, null, null);
        try {
            subscriber.start();
            Thread.sleep(2000);
            assertThat("a refused connection should not report itself as open", subscriber.isOpen(), is(false));
        } finally {
            subscriber.shutdown();
        }
    }
}

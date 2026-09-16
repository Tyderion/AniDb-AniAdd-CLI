package aniAdd.kodi;

import aniAdd.kodi.jsonrpc.KodiJsonRpc;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Short-lived request/response connection to Kodi's JSON-RPC websocket. Unlike {@link KodiNotificationSubscriber} it
 * matches replies to requests by id and lets callers wait for a specific notification.
 */
@Slf4j
public class KodiRpcClient extends WebSocketClient {
    private final Gson gson = new Gson();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final CompletableFuture<Void> opened = new CompletableFuture<>();
    private final Map<Integer, CompletableFuture<JsonObject>> pendingCalls = new ConcurrentHashMap<>();
    private final List<NotificationWaiter> notificationWaiters = new CopyOnWriteArrayList<>();

    private record NotificationWaiter(String method, CompletableFuture<JsonObject> future) {
    }

    public KodiRpcClient(URI serverUri) {
        super(serverUri);
    }

    /**
     * @return completes once the connection is open, or exceptionally if it could not be opened
     */
    public CompletableFuture<Void> open() {
        connect();
        return opened;
    }

    /**
     * @return the {@code result} element of the reply, or an exceptionally completed future on a JSON-RPC error
     */
    public CompletableFuture<JsonObject> call(KodiJsonRpc rpc) {
        val id = nextId.getAndIncrement();
        val future = new CompletableFuture<JsonObject>();
        pendingCalls.put(id, future);
        val request = rpc.setId(id).getAsJsonString();
        log.trace(STR."Sending kodi request: \{request}");
        try {
            send(request);
        } catch (Exception e) {
            pendingCalls.remove(id);
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Register before triggering whatever causes the notification, or it may arrive before anyone listens.
     *
     * @return completes with the {@code params} of the next notification with this method name
     */
    public CompletableFuture<JsonObject> nextNotification(String method) {
        val future = new CompletableFuture<JsonObject>();
        notificationWaiters.add(new NotificationWaiter(method, future));
        return future;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        opened.complete(null);
    }

    @Override
    public void onMessage(String message) {
        try {
            log.trace(STR."Received kodi message: \{message}");
            val json = gson.fromJson(message, JsonObject.class);
            if (json.has("id") && !json.get("id").isJsonNull()) {
                completeCall(json);
            } else if (json.has("method")) {
                val method = json.get("method").getAsString();
                val params = json.has("params") && json.get("params").isJsonObject() ? json.getAsJsonObject("params") : new JsonObject();
                for (val waiter : notificationWaiters) {
                    if (waiter.method().equals(method) && notificationWaiters.remove(waiter)) {
                        waiter.future().complete(params);
                    }
                }
            }
        } catch (Exception e) {
            log.warn(STR."Failed to handle kodi message '\{message}'", e);
        }
    }

    private void completeCall(JsonObject json) {
        val future = pendingCalls.remove(json.get("id").getAsInt());
        if (future == null) {
            return;
        }
        if (json.has("error")) {
            future.completeExceptionally(new KodiRpcException(json.get("error").toString()));
        } else {
            future.complete(json.has("result") && json.get("result").isJsonObject() ? json.getAsJsonObject("result") : new JsonObject());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        val cause = new KodiRpcException(STR."Connection to kodi closed (\{code}: \{reason})");
        opened.completeExceptionally(cause);
        pendingCalls.values().forEach(future -> future.completeExceptionally(cause));
        pendingCalls.clear();
        notificationWaiters.forEach(waiter -> waiter.future().completeExceptionally(cause));
        notificationWaiters.clear();
    }

    @Override
    public void onError(Exception ex) {
        opened.completeExceptionally(ex);
    }

    public static class KodiRpcException extends RuntimeException {
        public KodiRpcException(String message) {
            super(message);
        }
    }
}

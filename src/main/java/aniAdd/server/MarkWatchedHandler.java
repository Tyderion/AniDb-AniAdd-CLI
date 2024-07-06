package aniAdd.server;

import aniAdd.IAniAdd;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class MarkWatchedHandler implements HttpHandler {

    private final IAniAdd aniAdd;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_METHOD, 0);
            exchange.close();
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            val request = mapper.readValue(exchange.getRequestBody(), MarkWatchedRequest.class);
            val response = STR."Trying to mark file at path \{request.path} as watched";
            sendResponse(exchange, HttpURLConnection.HTTP_OK, response);
            Logger.getGlobal().log(Level.INFO, response);

        } catch (Exception e) {
            sendResponse(exchange, HttpURLConnection.HTTP_BAD_REQUEST, e.getMessage());
            Logger.getGlobal().log(Level.WARNING, e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private void sendResponse(HttpExchange exchange, int responseCode, String response) throws IOException {
        exchange.sendResponseHeaders(responseCode, response.length());
        exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
    }

    private static class MarkWatchedRequest {
        @JsonProperty
        private String path;
    }
}

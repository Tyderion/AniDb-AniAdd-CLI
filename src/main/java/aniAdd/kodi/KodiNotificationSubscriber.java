package aniAdd.kodi;

import aniAdd.IAniAdd;
import aniAdd.kodi.jsonrpc.GetEpisodeDetail;
import aniAdd.kodi.jsonrpc.GetMovieDetail;
import aniAdd.kodi.jsonrpc.KodiJsonRpc;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.val;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KodiNotificationSubscriber extends WebSocketClient {

    private final Gson gson = new GsonBuilder().setFieldNamingStrategy(f -> f.getName().toLowerCase()).create();
    private IAniAdd aniAdd;

    public KodiNotificationSubscriber(URI serverUri, IAniAdd aniAdd) {
        super(serverUri);
        this.aniAdd = aniAdd;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
    }

    @Override
    public void onMessage(String message) {
        Logger.getGlobal().log(Level.INFO, STR."Received message: \{message}");
        if (message == null || message.isEmpty()) {
            return;
        }
        val json = gson.fromJson(message, JsonObject.class);

        if (isMethod(json, "VideoLibrary.OnUpdate")) {
            val parameters = gson.fromJson(json.get("params").getAsJsonObject().get("data").toString(), VideoLibraryUpdateParams.class);
            handleVideoLibraryOnUpdate(parameters);
        }
        if (isResult(json, "episodedetails")) {
            val episodeDetail = gson.fromJson(json.get("result").getAsJsonObject().get("episodedetails").toString(), EpisodeDetail.class);
            System.out.println(STR."Episode details: \{episodeDetail}");
            handleEpisodeWatched(episodeDetail);
        }
        if (isResult(json, "moviedetails")) {
            val movieDetail = gson.fromJson(json.get("result").getAsJsonObject().get("moviedetails").toString(), MovieDetail.class);
            System.out.println(STR."Movie details: \{movieDetail}");
            handleMovieWatched(movieDetail);
        }
    }

    private boolean isMethod(JsonObject message, String method) {
        return message.has("method") && message.get("method").getAsString().equals(method);
    }

    private boolean isResult(JsonObject message, String result) {
        if  (message.has("result")) {
            val resultDict = message.get("result").getAsJsonObject();
            return resultDict.has(result) && resultDict.get(result).isJsonObject();
        }
        return false;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // The close codes are documented in class org.java_websocket.framing.CloseFrame
        System.out.println(
                STR."Connection closed by \{remote ? "remote peer" : "us"} Code: \{code} Reason: \{reason}");
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
        // if the error is fatal then onClose will be called additionally
    }

    private void handleVideoLibraryOnUpdate(VideoLibraryUpdateParams parameters) {
        System.out.println(STR."Video library updated with data: \{parameters}");
        switch (parameters.item.type) {
            case MOVIE -> sendRpcRequest(new GetMovieDetail()
                    .setMovieId(parameters.item.id)
                    .setProperties("file", "playcount"));
            case EPISODE -> sendRpcRequest(new GetEpisodeDetail()
                    .setEpisodeId(parameters.item.id)
                    .setProperties("file", "playcount"));
        }
    }

    private void sendRpcRequest(KodiJsonRpc rpc) {
        val request = rpc.getAsJsonString();
        Logger.getGlobal().log(Level.INFO, STR."Sending request: \{request}");
        send(request.getBytes(StandardCharsets.UTF_8));
    }

    private void handleEpisodeWatched(EpisodeDetail episodeDetail) {
        if (!episodeDetail.file.toLowerCase().contains("anime")) {
            Logger.getGlobal().log(Level.INFO, "Not an anime episode, skipping");
            return;
        }
        val localFilePath = getPath(episodeDetail.file, VideoLibraryUpdateParams.Type.EPISODE);
        if (episodeDetail.getPlayCount() > 0) {
            aniAdd.MarkFileAsWatched(localFilePath);
        }
    }

    private void handleMovieWatched(MovieDetail movieDetail) {
        if (!movieDetail.file.toLowerCase().contains("anime")) {
            Logger.getGlobal().log(Level.INFO, "Not an anime movie skipping");
            return;
        }
        val localFilePath = getPath(movieDetail.file, VideoLibraryUpdateParams.Type.MOVIE);
        if (movieDetail.getPlayCount() > 0) {
            aniAdd.MarkFileAsWatched(localFilePath);
        }
    }

    private String getPath(String file, VideoLibraryUpdateParams.Type type) {
        val config = aniAdd.getConfiguration();
        val normalizedPath = Paths.get(file).normalize();
        val relativePath = Paths.get(normalizedPath.getParent().getFileName().toString(), normalizedPath.getFileName().toString());
        return type == VideoLibraryUpdateParams.Type.EPISODE ? config.getEpisodePath(relativePath.toString()) : config.getMoviePath(relativePath.toString());
    }

    @Data
    private static class EpisodeDetail {
        private String file;
        private int playCount;
        private int episodeId;
    }

    @Data
    private static class MovieDetail {
        private String file;
        private int playCount;
        private int movieId;
    }

    @Data
    private static class VideoLibraryUpdateParams {
        private int playCount;
        private Item item;

        @Data
        private static class Item {
            private int id;
            private Type type;
        }


        private static enum Type {
            @SerializedName("movie")
            MOVIE,
            @SerializedName("episode")
            EPISODE
        }
    }
}

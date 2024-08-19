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
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
public class KodiNotificationSubscriber extends WebSocketClient {

    private final Gson gson = new GsonBuilder().setFieldNamingStrategy(f -> f.getName().toLowerCase()).create();
    private final IAniAdd aniAdd;
    private final String pathFilter;

    public KodiNotificationSubscriber(URI serverUri, IAniAdd aniAdd, String pathFilter) {
        super(serverUri);
        this.aniAdd = aniAdd;
        this.pathFilter = pathFilter.toLowerCase();
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.debug(STR."Connection opened \{handshakedata.getHttpStatus()} \{handshakedata.getHttpStatusMessage()}");
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        val json = gson.fromJson(message, JsonObject.class);

        if (isMethod(json, "VideoLibrary.OnUpdate")) {
            val parameters = gson.fromJson(json.get("params").getAsJsonObject().get("data").toString(), VideoLibraryUpdateParams.class);
            if (parameters.playCount > 0) {
                handleVideoLibraryOnUpdate(parameters);
            }
        }
        if (isResult(json, "episodedetails")) {
            val episodeDetail = gson.fromJson(json.get("result").getAsJsonObject().get("episodedetails").toString(), EpisodeDetail.class);
            log.trace(STR."Episode details: \{episodeDetail}");
            handleEpisodeWatched(episodeDetail);
        }
        if (isResult(json, "moviedetails")) {
            val movieDetail = gson.fromJson(json.get("result").getAsJsonObject().get("moviedetails").toString(), MovieDetail.class);
            log.trace(STR."Movie details: \{movieDetail}");
            handleMovieWatched(movieDetail);
        }
    }

    private boolean isMethod(JsonObject message, String method) {
        return message.has("method") && message.get("method").getAsString().equals(method);
    }

    private boolean isResult(JsonObject message, String result) {
        if (message.has("result")) {
            val resultDict = message.get("result").getAsJsonObject();
            return resultDict.has(result) && resultDict.get(result).isJsonObject();
        }
        return false;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn(STR."Connection closed by \{remote ? "remote peer" : "us"} Code: \{code} Reason: \{reason}. Will try to reconnect in 5s");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        connect();
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
        System.exit(1);
        // if the error is fatal then onClose will be called additionally
    }

    private void handleVideoLibraryOnUpdate(VideoLibraryUpdateParams parameters) {
        log.debug(STR."Handling video library update: \{parameters}");
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
        log.trace(STR."Sending request: \{request}");
        send(request.getBytes(StandardCharsets.UTF_8));
    }

    private void handleEpisodeWatched(EpisodeDetail episodeDetail) {
        if (!episodeDetail.file.toLowerCase().contains(pathFilter)) {
            log.trace(STR."Not an anime episode '\{episodeDetail.file}', skipping");
            return;
        }
        val localFilePath = getPath(episodeDetail.file, VideoLibraryUpdateParams.Type.EPISODE);
        if (localFilePath.isEmpty()) {
            log.warn(STR."Could not get path for movie '\{episodeDetail.file}', skipping");
            return;
        }
        log.debug(STR."Episode file path: \{localFilePath.get()}");
        if (episodeDetail.getPlayCount() > 0) {
            aniAdd.MarkFileAsWatched(localFilePath.get());
        }
    }

    private void handleMovieWatched(MovieDetail movieDetail) {
        if (!movieDetail.file.toLowerCase().contains("anime")) {
            log.trace(STR."Not an anime movie '\{movieDetail.file}', skipping");
            return;
        }
        val localFilePath = getPath(movieDetail.file, VideoLibraryUpdateParams.Type.MOVIE);
        if (localFilePath.isEmpty()) {
            log.warn(STR."Could not get path for movie '\{movieDetail.file}', skipping");
            return;
        }
        log.debug(STR."Movie file path: \{localFilePath.get()}");
        if (movieDetail.getPlayCount() > 0) {
            aniAdd.MarkFileAsWatched(localFilePath.get());
        }
    }

    @NotNull
    private Optional<Path> getPath(String file, VideoLibraryUpdateParams.Type type) {
        val config = aniAdd.getConfiguration().paths();
        var pathParts = file.split("/");
        if (pathParts.length == 1) {
            // nothing was split, so we assume it's a windows path
            pathParts = file.split("\\\\");
        }
        if (pathParts.length < 2) {
            log.warn(STR."Could not split path '\{file}'");
            return Optional.empty();
        }
        val relativePath = Paths.get(pathParts[pathParts.length - 2], pathParts[pathParts.length - 1]);
        return type == VideoLibraryUpdateParams.Type.EPISODE ? config.getEpisodePath(relativePath) : config.getMoviePath(relativePath);
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


        private enum Type {
            @SerializedName("movie")
            MOVIE,
            @SerializedName("episode")
            EPISODE,
            @SerializedName("tvshow")
            TVSHOW,
            @SerializedName("musicvideo")
            MUSICVIDEO
        }
    }
}

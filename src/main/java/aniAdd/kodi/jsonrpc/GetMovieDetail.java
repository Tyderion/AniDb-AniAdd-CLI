package aniAdd.kodi.jsonrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

public class GetMovieDetail extends KodiJsonRpc {
    public static final String GET_MOVIE_DETAILS = "VideoLibrary.GetMovieDetails";

    public GetMovieDetail() {
        super(GET_MOVIE_DETAILS);
    }

    public GetMovieDetail setMovieId(int movieId) {
        addParam("movieid", movieId);
        return this;
    }

    public GetMovieDetail setProperties(String... properties) {
        JsonArray props = new JsonArray();
        for (String property : properties) props.add(new JsonPrimitive(property));
        addParam("properties", props);
        return this;
    }
}

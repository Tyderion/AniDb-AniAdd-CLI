package aniAdd.kodi.jsonrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

public class GetEpisodeDetail extends KodiJsonRpc {
    public static final String GET_EPISODE_DETAILS = "VideoLibrary.GetEpisodeDetails";

    public GetEpisodeDetail() {
        super(GET_EPISODE_DETAILS);
    }

    public GetEpisodeDetail setEpisodeId(int episodeId) {
        addParam("episodeid", episodeId);
        return this;
    }

    public GetEpisodeDetail setProperties(String... properties) {
        JsonArray props = new JsonArray();
        for (String property : properties) props.add(new JsonPrimitive(property));
        addParam("properties", props);
        return this;
    }
}

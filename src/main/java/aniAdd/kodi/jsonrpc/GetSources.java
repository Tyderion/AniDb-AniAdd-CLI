package aniAdd.kodi.jsonrpc;

public class GetSources extends KodiJsonRpc {
    public static final String GET_SOURCES = "Files.GetSources";

    public GetSources() {
        super(GET_SOURCES);
        addParam("media", "video");
    }
}

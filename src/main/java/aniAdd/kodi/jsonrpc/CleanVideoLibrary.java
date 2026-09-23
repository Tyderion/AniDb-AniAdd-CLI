package aniAdd.kodi.jsonrpc;

public class CleanVideoLibrary extends KodiJsonRpc {
    public static final String CLEAN = "VideoLibrary.Clean";
    public static final String ON_CLEAN_FINISHED = "VideoLibrary.OnCleanFinished";

    /**
     * @param content must match what the source is set to in Kodi ("tvshows", "movies", ...), or Kodi accepts the
     *                request and cleans nothing
     */
    public CleanVideoLibrary(String directory, String content, boolean showDialogs) {
        super(CLEAN);
        addParam("directory", directory);
        addParam("content", content);
        addParam("showdialogs", showDialogs);
    }
}

package aniAdd.kodi.jsonrpc;

public class ScanVideoLibrary extends KodiJsonRpc {
    public static final String SCAN = "VideoLibrary.Scan";
    public static final String ON_SCAN_FINISHED = "VideoLibrary.OnScanFinished";

    public ScanVideoLibrary(String directory, boolean showDialogs) {
        super(SCAN);
        addParam("directory", directory);
        addParam("showdialogs", showDialogs);
    }
}

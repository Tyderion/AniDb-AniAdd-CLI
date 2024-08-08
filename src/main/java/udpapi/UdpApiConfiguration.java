package udpapi;

import java.time.Duration;

public final class UdpApiConfiguration {
    public static final String ANIDB_CLIENT_TAG = "AniAddCLI";
    public static final int ANIDB_CLIENT_VERSION = 5;
    public static final int ANIDB_PROTOCOL_VERSION = 3;
    public static final Duration COMMAND_INTERVAL_MS = Duration.ofSeconds(4);
    public static final Duration LONG_WAIT_TIME = Duration.ofMinutes(60);
    public static final Duration LOGOUT_AFTER = Duration.ofMinutes(30);
}

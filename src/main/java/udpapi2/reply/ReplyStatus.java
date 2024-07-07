package udpapi2.reply;

public enum ReplyStatus {
    LOGIN_ACCEPTED(200),
    LOGIN_ACCEPTED_NEW_VERSION(201),
    LOGIN_FAILED(500),
    LOGIN_FIRST(501),
    ACCESS_DENIED(502),
    CLIENT_VERSION_OUTDATED(503),
    CLIENT_BANNED(504),
    ILLEGAL_INPUT_OR_ACCESS_DENIED(505),
    INVALID_SESSION(506),
    BANNED(555),
    UNKNOWN_COMMAND(598),
    INTERNAL_SERVER_ERROR(600),
    ANIDB_OUT_OF_SERVICE(601),
    SERVER_BUSY(602),
    TIMEOUT(604);



    private final int value;

    ReplyStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ReplyStatus fromString(String value) {
        for (ReplyStatus status : ReplyStatus.values()) {
            if (String.valueOf(status.getValue()).equals(value)) {
                return status;
            }
        }
        return null;
    }
}

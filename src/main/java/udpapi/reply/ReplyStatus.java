package udpapi.reply;

public enum ReplyStatus {
    LOGIN_ACCEPTED(200),
    LOGIN_ACCEPTED_NEW_VERSION(201),
    LOGGED_OUT(203),
    MYLIST_ENTRY_ADDED(210),
    FILE(220),
    MYLIST(221),
    PONG(300),
    FILE_ALREADY_IN_MYLIST(310),
    MYLIST_ENTRY_EDITED(311),
    MULTIPLE_MYLIST_ENTRIES(312),
    NO_SUCH_FILE(320),
    NO_SUCH_ENTRY(321),
    MULTIPLE_FILES_FOUND(322),
    NO_SUCH_ANIME(330),
    NO_SUCH_GROUP(350),
    NOT_LOGGED_IN(403),
    NO_SUCH_MYLIST_ENTRY(411),
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

    public boolean success() {
        switch (this) {
            case LOGIN_FAILED, LOGIN_FIRST, NOT_LOGGED_IN,
                 INVALID_SESSION,
                 TIMEOUT -> {
                return false;
            }
            default -> {
                return !isFatal();
            }
        }
    }

    public boolean isFatal() {
        switch (this) {
            case BANNED, CLIENT_BANNED,
                 ILLEGAL_INPUT_OR_ACCESS_DENIED, INTERNAL_SERVER_ERROR,
                 ANIDB_OUT_OF_SERVICE, SERVER_BUSY -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}

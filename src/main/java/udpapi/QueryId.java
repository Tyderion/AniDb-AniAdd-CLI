package udpapi;

public class QueryId {
    private static int queryId = 0;

    public static int Next() {
        return queryId++;
    }

    public static void reset() {
        queryId = 0;
    }
}

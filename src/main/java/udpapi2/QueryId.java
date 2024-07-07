package udpapi2;

public class QueryId {
    private static int queryId = 0;

    public static int Next() {
        return queryId++;
    }

    public static void Reset() {
        queryId = 0;
    }
}

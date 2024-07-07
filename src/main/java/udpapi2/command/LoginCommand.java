package udpapi2.command;

import udpapi2.QueryId;
import udpapi2.UdpApi;

public class LoginCommand extends CommandWrapper {
    private static final String AUTH_ACTION = "AUTH";

    private LoginCommand(Command command) throws IllegalArgumentException {
        super(command);
    }

    public static LoginCommand Create(String username, String password) throws IllegalArgumentException {
        if (username == null || username.isEmpty() || username.isBlank()) {
            throw new IllegalArgumentException("username empty");
        }
        if (password == null || password.isEmpty() || password.isBlank()) {
            throw new IllegalArgumentException("password empty");
        }
        return new LoginCommand(Command.builder()
                .action(AUTH_ACTION)
                .identifier(AUTH_ACTION.toLowerCase())
                .queryId(QueryId.Next())
                .tag(null)
                .needsLogin(false)
                .parameter("nat", "1")
                .parameter("enc", "UTF8")
                .parameter("comp", "1")
                .parameter("user", username)
                .parameter("pass", password)
                .parameter("protover", String.valueOf(UdpApi.PROTOCOL_VERSION))
                .parameter("client", UdpApi.CLIENT_TAG)
                .parameter("clientver", String.valueOf(UdpApi.CLIENT_VERSION))
                .build());
    }
}

package udpapi2.command;

import lombok.Builder;
import lombok.experimental.SuperBuilder;
import udpapi2.QueryId;
import udpapi2.UdpApi;

@SuperBuilder
public class LoginCommand extends Command {
    private static final String ACTION = "AUTH";

    public static LoginCommand Create(String username, String password) throws IllegalArgumentException {
        if (username == null || username.isEmpty() || username.isBlank()) {
            throw new IllegalArgumentException("username empty");
        }
        if (password == null || password.isEmpty() || password.isBlank()) {
            throw new IllegalArgumentException("password empty");
        }
        return LoginCommand.builder()
                .action(ACTION)
                .identifier(ACTION.toLowerCase())
                .queryId(QueryId.Next())
                .tag(null)
                .needsLogin(false)
                .parameter("client", UdpApi.CLIENT_TAG.toLowerCase())
                .parameter("clientver", String.valueOf(UdpApi.CLIENT_VERSION))
                .parameter("comp", "1")
                .parameter("enc", "UTF8")
                .parameter("nat", "1")
                .parameter("pass", password)
                .parameter("protover", String.valueOf(UdpApi.PROTOCOL_VERSION))
                .parameter("user", username.toLowerCase())
                .build();
    }
}

package udpapi.command;

import lombok.experimental.SuperBuilder;
import udpapi.QueryId;
import udpapi.UdpApiConfiguration;

import java.util.Set;

@SuperBuilder
public class LoginCommand extends Command {
    private static final String ACTION = "AUTH";


    @Override
    protected Set<String> getSensitiveParameters() {
        return Set.of("user", "pass");
    }

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
                .parameter("client", UdpApiConfiguration.ANIDB_CLIENT_TAG.toLowerCase())
                .parameter("clientver", String.valueOf(UdpApiConfiguration.ANIDB_CLIENT_VERSION))
                .parameter("comp", "1")
                .parameter("enc", "UTF8")
                .parameter("nat", "1")
                .parameter("pass", password)
                .parameter("protover", String.valueOf(UdpApiConfiguration.ANIDB_PROTOCOL_VERSION))
                .parameter("user", username.toLowerCase())
                .build();
    }
}

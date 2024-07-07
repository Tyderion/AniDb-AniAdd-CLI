package udpapi2;

import aniAdd.IAniAdd;
import aniAdd.Modules.BaseModule;
import aniAdd.config.AniConfiguration;
import lombok.val;
import udpapi2.command.Command;

public class UdpApi extends BaseModule {
    public static final int PROTOCOL_VERSION = 3;
    public static final String CLIENT_TAG = "AniAddCLI";
    public static final int CLIENT_VERSION = 4;
    private eModState modState = eModState.New;
    private AniConfiguration configuration;

    @Override
    public String ModuleName() {
        return "UdpApi";
    }

    @Override
    public eModState ModState() {
        return modState;
    }

    @Override
    public void Initialize(IAniAdd aniAdd, AniConfiguration configuration) {
        this.configuration = configuration;
        modState = eModState.Initializing;
        val cmd = Command.builder().
                action("init").
                identifier("AniAdd").
                tag("UdpApi").
                needsLogin(false).
                build();

        cmd.toBuilder().parameter("version", "1").build();

    }

    @Override
    public void Terminate() {

    }
}

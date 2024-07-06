package aniAdd.startup.commands.anidb;

import aniAdd.AniAdd;
import aniAdd.Communication;
import aniAdd.IAniAdd;
import aniAdd.Modules.IModule;
import aniAdd.config.AniConfiguration;
import aniAdd.startup.commands.CliCommand;
import lombok.val;
import picocli.CommandLine;
import udpApi.Mod_UdpApi;

@CommandLine.Command(
        subcommands = {ScanCommand.class, ServerCommand.class, TagsCommand.class},
        name = "anidb",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "AniDb handling")
public class AnidbCommand {
    @CommandLine.Option(names = {"-u", "--username"}, description = "The AniDB username", required = true, scope = CommandLine.ScopeType.INHERIT)
    String username;
    @CommandLine.Option(names = {"-p", "--password"}, description = "The AniDB password", required = true, scope = CommandLine.ScopeType.INHERIT)
    String password;
    @CommandLine.ParentCommand
    private CliCommand parent;

    AniConfiguration getConfiguration() {
        return parent.getConfiguration();
    }

    IAniAdd initializeAniAdd() {
        val aniAdd = new AniAdd(parent.getConfiguration());

        aniAdd.addComListener(comEvent -> {
            if (comEvent.EventType() == Communication.CommunicationEvent.EventType.Information) {
                if (comEvent.Params(0) == IModule.eModState.Initialized) {
                    Mod_UdpApi api = aniAdd.GetModule(Mod_UdpApi.class);
                    api.setPassword(password);
                    api.setUsername(username);

                    api.authenticate();
                }
            }
        });
        return aniAdd;
    }
}

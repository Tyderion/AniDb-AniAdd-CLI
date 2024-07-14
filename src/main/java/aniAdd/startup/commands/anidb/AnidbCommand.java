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
import udpapi2.NewUdpApi;
import udpapi2.UdpApi;

import java.util.concurrent.ScheduledExecutorService;

@CommandLine.Command(
        subcommands = {ScanCommand.class, ServerCommand.class, TagsCommand.class, TestCommand.class},
        name = "anidb",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "AniDb handling")
public class AnidbCommand {
    @CommandLine.Option(names = {"-u", "--username"}, description = "The AniDB username", required = true, scope = CommandLine.ScopeType.INHERIT)
    String username;
    @CommandLine.Option(names = {"-p", "--password"}, description = "The AniDB password", required = true, scope = CommandLine.ScopeType.INHERIT)
    String password;

    @CommandLine.Option(names = {"--localport"}, description = "The AniDB password", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "3333")
    int localPort;

    @CommandLine.ParentCommand
    private CliCommand parent;

    AniConfiguration getConfiguration() {
        return parent.getConfiguration();
    }

    IAniAdd initializeAniAdd(boolean terminateOnCompletion, ScheduledExecutorService executorService) {
        val udpApi = new NewUdpApi(executorService, localPort);
        udpApi.setPassword(password);
        udpApi.setUsername(username);

        val aniAdd = new AniAdd(getConfiguration(), udpApi);

        udpApi.Initialize(getConfiguration());

        aniAdd.Start(terminateOnCompletion);
        return aniAdd;
    }
}

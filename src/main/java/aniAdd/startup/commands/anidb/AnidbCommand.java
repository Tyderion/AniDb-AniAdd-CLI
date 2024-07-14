package aniAdd.startup.commands.anidb;

import aniAdd.AniAdd;
import aniAdd.IAniAdd;
import aniAdd.config.AniConfiguration;
import aniAdd.startup.commands.CliCommand;
import fileprocessor.FileProcessor;
import lombok.val;
import picocli.CommandLine;
import processing.Mod_EpProcessing;
import udpapi2.UdpApi;

import java.util.concurrent.ScheduledExecutorService;

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

    @CommandLine.Option(names = {"--localport"}, description = "The AniDB password", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "3333")
    int localPort;

    @CommandLine.ParentCommand
    private CliCommand parent;

    AniConfiguration getConfiguration() {
        return parent.getConfiguration();
    }

    IAniAdd initializeAniAdd(boolean terminateOnCompletion, ScheduledExecutorService executorService) {
        val udpApi = new UdpApi(executorService, localPort, username, password);
        udpApi.Initialize(getConfiguration());

        val processing = new Mod_EpProcessing(getConfiguration(), udpApi);
        val fileProcessor = new FileProcessor(processing, getConfiguration(), executorService);

        return new AniAdd(getConfiguration(), udpApi, terminateOnCompletion, fileProcessor, processing, _ -> executorService.shutdownNow());
    }
}

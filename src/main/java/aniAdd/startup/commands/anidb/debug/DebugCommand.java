package aniAdd.startup.commands.anidb.debug;

import aniAdd.config.AniConfiguration;
import aniAdd.startup.commands.anidb.AnidbCommand;
import aniAdd.startup.commands.debug.InsertResponse;
import lombok.Getter;
import picocli.CommandLine;
import udpapi.UdpApi;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

@Getter
@CommandLine.Command(name = "debug",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Debugging commands",
        subcommands = {InsertFile.class})
public class DebugCommand {

    @CommandLine.ParentCommand
    private AnidbCommand parent;

    public UdpApi getUdpApi(AniConfiguration configuration, ScheduledExecutorService executorService) {
        return parent.getUdpApi(configuration, executorService);
    }

    AniConfiguration getConfiguration() {
        return parent.getConfigurationOrDefault().get();
    }

    public Path getDbPath() {
        return parent.getDbPath();
    }
}

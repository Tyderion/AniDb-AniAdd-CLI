package startup.commands.anidb.debug;

import config.CliConfiguration;
import lombok.Getter;
import picocli.CommandLine;
import startup.commands.anidb.AnidbCommand;
import udpapi.UdpApi;

import java.nio.file.Path;
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

    public UdpApi getUdpApi(CliConfiguration configuration, ScheduledExecutorService executorService) {
        return parent.getUdpApi(configuration, executorService);
    }

    CliConfiguration getConfiguration() {
        return parent.getConfiguration();
    }

    public Path getDbPath() {
        return parent.getDbPath();
    }
}
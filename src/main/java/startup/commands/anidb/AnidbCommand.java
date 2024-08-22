package startup.commands.anidb;

import aniAdd.AniAdd;
import aniAdd.IAniAdd;
import cache.AniDBFileRepository;
import config.blocks.AniDbConfig;
import config.blocks.FileConfig;
import fileprocessor.DeleteEmptyChildDirectoriesRecursively;
import fileprocessor.FileProcessor;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import processing.EpisodeProcessing;
import processing.FileHandler;
import processing.FileInfo;
import startup.commands.ConfigRequiredCommand;
import startup.commands.anidb.debug.DebugCommand;
import startup.commands.util.CommandHelper;
import startup.validation.validators.config.MapConfig;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.port.Port;
import udpapi.UdpApi;
import udpapi.reply.ReplyStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@CommandLine.Command(
        subcommands = {ScanCommand.class, KodiWatcherCommand.class, WatchCommand.class, DebugCommand.class},
        name = "anidb",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "AniDb handling")
public class AnidbCommand extends ConfigRequiredCommand {
    @MapConfig(configPath = "anidb.username", envVariableName = "ANIDB_USERNAME", required = true)
    @CommandLine.Option(names = {"-u", "--username"}, description = "The AniDB username", scope = CommandLine.ScopeType.INHERIT)
    @NonBlank String username;

    @MapConfig(configPath = "anidb.password", envVariableName = "ANIDB_PASSWORD", required = true, configMustBeNull = true)
    @CommandLine.Option(names = {"-p", "--password"}, description = "The AniDB password", scope = CommandLine.ScopeType.INHERIT)
    @NonBlank String password;

    @MapConfig(configPath = "anidb.localPort")
    @CommandLine.Option(names = {"--localport"}, description = "The local port to use to connect to anidb", scope = CommandLine.ScopeType.INHERIT)
    @Port int localPort;

    @MapConfig(configPath = "anidb.exitOnBan")
    @CommandLine.Option(names = {"--exit-on-ban"}, description = "Exit the application if the user is banned", scope = CommandLine.ScopeType.INHERIT)
    boolean exitOnBan;

    @Getter
    @NonBlank
    @MapConfig(configPath = "anidb.cache.db")
    @CommandLine.Option(names = {"--db"}, description = "The path to the sqlite db", scope = CommandLine.ScopeType.INHERIT)
    Path dbPath;

    @MapConfig(configPath = "anidb")
    AniDbConfig aniDbConfig;

    @MapConfig(configPath = "file")
    FileConfig fileConfig;

    public UdpApi getUdpApi(ScheduledExecutorService executorService) {
        return new UdpApi(executorService, aniDbConfig);
    }

    public Optional<IAniAdd> initializeAniAdd(boolean terminateOnCompletion, ScheduledExecutorService
            executorService, DoOnFileSystem fileSystem, Path inputDirectory, SessionFactory sessionFactory) {
        val udpApi = getUdpApi(executorService);
        val fileHandler = new FileHandler();
        val fileRepository = new AniDBFileRepository(sessionFactory);

        val config = getConfiguration();
        val processing = new EpisodeProcessing(config, udpApi, fileSystem, fileHandler, fileRepository);
        val fileProcessor = new FileProcessor(processing, FileInfo.Configuration.of(config.file(), config.mylist()), executorService);

        if (fileConfig.move().deleteEmptyDirs() && inputDirectory != null) {
            processing.addListener(event -> {
                if (event == EpisodeProcessing.ProcessingEvent.Done) {
                    fileSystem.run(new DeleteEmptyChildDirectoriesRecursively(inputDirectory));
                }
            });
        }

        val aniAdd = new AniAdd(config, udpApi, terminateOnCompletion, fileProcessor, processing, _ -> {
            log.info("Shutdown complete");
            executorService.shutdownNow();
        });
        if (exitOnBan) {
            udpApi.registerCallback(ReplyStatus.BANNED, _ -> {
                log.error("User is banned. Exiting.");
                aniAdd.Stop();
                // Make sure we shut down even if terminateOnCompletion is false
                if (!executorService.isShutdown()) {
                    executorService.shutdownNow();
                }
            });
        }

        return Optional.of(aniAdd);
    }

    public static List<String> getOptions() {
        return CommandHelper.getOptions(AnidbCommand.class, Set.of("password"));
    }

    public static String getName() {
        return CommandHelper.getName(AnidbCommand.class);
    }
}

package startup.commands.anidb;

import aniAdd.AniAdd;
import aniAdd.IAniAdd;
import cache.AniDBFileRepository;
import cache.AnimeMappingRepository;
import cache.AnimeXmlRepository;
import config.blocks.AniDbConfig;
import config.blocks.FileConfig;
import config.blocks.KodiConfig;
import config.blocks.TagsConfig;
import fileprocessor.DeleteEmptyChildDirectoriesRecursively;
import fileprocessor.FileProcessor;
import kodi.KodiMetadataGenerator;
import kodi.library.KodiLibraryScanner;
import kodi.tmdb.TmDbApi;
import kodi.tvdb.TvDbApi;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.hibernate.SessionFactory;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import processing.EpisodeProcessing;
import processing.FileHandler;
import startup.commands.ConfigRequiredCommand;
import startup.commands.anidb.debug.DebugCommand;
import startup.commands.util.CommandHelper;
import startup.validation.validators.config.MapConfig;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.port.Port;
import udpapi.UdpApi;
import udpapi.reply.ReplyStatus;
import utils.http.DownloadHelper;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
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
    @Port Integer localPort;

    @MapConfig(configPath = "anidb.exitOnBan")
    @CommandLine.Option(names = {"--exit-on-ban"}, description = "Exit the application if the user is banned", scope = CommandLine.ScopeType.INHERIT)
    Boolean exitOnBan;

    @NonBlank
    @MapConfig(configPath = "anidb.cache.db")
    @CommandLine.Option(names = {"--db"}, description = "The path to the sqlite db", scope = CommandLine.ScopeType.INHERIT)
    Path dbPath;

    @MapConfig(configPath = "kodi.metadata.tmDbApiToken", envVariableName = "TMDB_ACCESS_TOKEN", required = true, configMustBeNull = true)
    @CommandLine.Option(names = {"--tmDbApiToken"}, description = "Token to access tmdb api", scope = CommandLine.ScopeType.INHERIT)
    @NonBlank String tmDbApiToken;

    @MapConfig(configPath = "kodi.metadata.tvDbApiKey", envVariableName = "TVDB_APIKEY", required = true, configMustBeNull = true)
    @CommandLine.Option(names = {"--tvDbApiKey"}, description = "ApiKey to access tvdb api", scope = CommandLine.ScopeType.INHERIT)
    @NonBlank String tvDbApiKey;

    @MapConfig(configPath = "anidb")
    AniDbConfig aniDbConfig;

    @MapConfig(configPath = "file")
    FileConfig fileConfig;

    @MapConfig(configPath = "tags")
    TagsConfig tagsConfig;

    @MapConfig(configPath = "kodi")
    KodiConfig kodiConfig;


    public UdpApi getUdpApi(ScheduledExecutorService executorService) {
        return new UdpApi(executorService, aniDbConfig);
    }

    public Optional<IAniAdd> initializeAniAdd(boolean terminateOnCompletion, ScheduledExecutorService
            executorService, DoOnFileSystem fileSystem, Path inputDirectory, SessionFactory sessionFactory) {
        val libraryScanProblems = kodiConfig.libraryScan().problems();
        if (!libraryScanProblems.isEmpty()) {
            libraryScanProblems.forEach(problem -> log.error(STR."Invalid configuration: \{problem}"));
            return Optional.empty();
        }
        val udpApi = getUdpApi(executorService);
        val fileHandler = new FileHandler();
        val fileRepository = new AniDBFileRepository(sessionFactory);
        val tvDbApi = new TvDbApi(kodiConfig.metadata().tvDbApiKey(), executorService);
        val tmDbApi = new TmDbApi(kodiConfig.metadata().tmDbApiToken(), executorService);
        val animeXmlRepository = new AnimeXmlRepository(sessionFactory);
        val animeMappingRepository = new AnimeMappingRepository(sessionFactory);
        val kodiMetadataGenerator = new KodiMetadataGenerator(
                new DownloadHelper(executorService), tvDbApi, tmDbApi, animeXmlRepository, animeMappingRepository,
                aniDbConfig.cache().ttlInDays(), kodiConfig.metadata().animeMappingUrl(),
                kodiConfig.metadata().overwrite());
        val processing = new EpisodeProcessing(fileConfig, tagsConfig, aniDbConfig, kodiConfig, udpApi, kodiMetadataGenerator, fileSystem, fileHandler, fileRepository);
        val fileProcessor = new FileProcessor(processing, fileConfig, executorService);

        if (fileConfig.move().deleteEmptyDirs() && inputDirectory != null) {
            processing.addListener(event -> {
                if (event == EpisodeProcessing.ProcessingEvent.Done) {
                    fileSystem.run(new DeleteEmptyChildDirectoriesRecursively(inputDirectory));
                }
            });
        }

        Supplier<CompletableFuture<Void>> afterBatch = () -> CompletableFuture.completedFuture(null);
        if (kodiConfig.libraryScan().enabled()) {
            val libraryScanner = new KodiLibraryScanner(() -> kodiConfig);
            processing.addFileFinishedListener(libraryScanner::onFileFinished);
            afterBatch = libraryScanner::onBatchDone;
        }

        val aniAdd = new AniAdd(udpApi, terminateOnCompletion, fileProcessor, processing, _ -> {
            log.info("Shutdown complete");
            executorService.shutdownNow();
        }, afterBatch);
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

    public static String getName() {
        return CommandHelper.getName(AnidbCommand.class);
    }
}

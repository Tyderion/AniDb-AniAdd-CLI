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

    // Required, with no default, on purpose. A default silently picked a location: relative to wherever the
    // command happened to start, so the same config could open a different cache from the IDE than from a
    // shell or a container. Starting on an empty cache means looking every file up again, which is what gets
    // an AniDB account banned, so a missing cache is refused rather than guessed at.
    @NonBlank(message = """
            anidb.cache.db is not set. Set it in the config file, or pass --db.
            Earlier versions defaulted to aniAdd.sqlite in the directory the command was run from. If you relied \
            on that, your cache is still there; keep using it by adding this to your config:
              anidb:
                cache:
                  db: /path/to/that/directory/aniAdd.sqlite
            Starting without it would mean looking every file up again.""")
    @MapConfig(configPath = "anidb.cache.db")
    @CommandLine.Option(names = {"--db"}, description = "The path to the sqlite db", scope = CommandLine.ScopeType.INHERIT)
    Path dbPath;

    /**
     * Refuses a config whose relative cache path has moved. Relative paths used to resolve against the working
     * directory and now resolve next to the config file, so an existing config can silently switch to a new,
     * empty cache and look every file up again, which is what gets an AniDB account banned. Only when the old
     * location has a cache and the new one does not: a fresh setup, where neither exists, starts normally.
     * dbPath still holds only what was typed on the command line here, so an explicit --db skips the check.
     */
    @Override
    protected String configProblem(String content) {
        if (dbPath != null) {
            return null;
        }
        return cacheRelocationProblem(rawCachePath(content), configPath, Path.of("").toAbsolutePath());
    }

    /** The rule on its own, so it can be tested without depending on the working directory. */
    public static String cacheRelocationProblem(String raw, Path configFile, Path workingDirectory) {
        if (raw == null || raw.isBlank() || Path.of(raw).isAbsolute()) {
            return null;
        }
        val now = utils.config.ConfigFileParser.resolve(Path.of(raw), utils.config.ConfigFileParser.baseDirectoryOf(configFile));
        val before = workingDirectory.resolve(raw).normalize();
        if (now.equals(before) || java.nio.file.Files.exists(now) || !java.nio.file.Files.exists(before)) {
            return null;
        }
        return String.join("\n",
                "anidb.cache.db is relative, and relative paths now resolve next to the config file, not the directory the command runs from.",
                "It now means:     " + now + "   (does not exist)",
                "It used to mean:  " + before + "   (exists)",
                "Refusing to start on an empty cache, which would look every file up again. To keep using your cache, set:",
                "  anidb:",
                "    cache:",
                "      db: " + before);
    }

    @SuppressWarnings("unchecked")
    private static String rawCachePath(String content) {
        try {
            val root = new org.yaml.snakeyaml.Yaml().load(content);
            if (!(root instanceof java.util.Map<?, ?> map)) return null;
            val anidb = map.get("anidb");
            val cache = anidb instanceof java.util.Map<?, ?> a ? a.get("cache") : null;
            val db = cache instanceof java.util.Map<?, ?> c ? c.get("db") : null;
            return db == null ? null : db.toString();
        } catch (RuntimeException e) {
            // The real parser reports malformed YAML properly; this check only ever declines.
            return null;
        }
    }

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
        val confineTo = fileConfig.move().confineTo();
        if (confineTo != null && inputDirectory != null && !processing.Confinement.contains(confineTo, inputDirectory)) {
            log.error(STR."Refusing to process \{inputDirectory}: file.move.confineTo allows only \{confineTo}");
            return Optional.empty();
        }
        val udpApi = getUdpApi(executorService);
        processing.IFileHandler fileHandler = confineTo == null
                ? new FileHandler()
                : new processing.ConfinedFileHandler(new FileHandler(), confineTo);
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
            processing.addScanRunFinishedListener(libraryScanner::onScanRunFinished);
            afterBatch = libraryScanner::lastScan;
        }

        val aniAdd = new AniAdd(udpApi, terminateOnCompletion, fileProcessor, processing, _ -> {
            log.info("Shutdown complete");
            executorService.shutdownNow();
        }, afterBatch, fileConfig, kodiConfig);
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

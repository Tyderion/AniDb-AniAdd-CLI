package startup.commands.anidb;

import cache.PersistenceConfiguration;
import config.blocks.AniDbConfig;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import startup.commands.util.CommandHelper;
import startup.validation.validators.config.MapConfig;
import startup.validation.validators.nonblank.NonBlank;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A pass over an existing library: identify every file, convert the ones matching the configured
 * input formats, and leave everything else alone. Identical to `scan` except that transcoding is on
 * whether or not the config says so, which is what makes it safe to point at series/ or movies/
 * without editing the config first.
 */
@Slf4j
@CommandLine.Command(name = "transcode", mixinStandardHelpOptions = true, version = "1.0",
        description = "Re-encodes matching files in a directory into the configured output format")
public class TranscodeCommand implements Callable<Integer> {
    @NonBlank
    @CommandLine.Parameters(index = "0", description = "The directory to walk.")
    private Path directory;

    @CommandLine.ParentCommand
    private AnidbCommand parent;

    @MapConfig(configPath = "anidb")
    private AniDbConfig aniDbConfig;

    @Override
    public Integer call() throws Exception {
        parent.transcodeConfig.enabled(true);
        val configError = parent.transcodeConfig.validationError();
        if (configError.isPresent()) {
            log.error(STR."Refusing to start: \{configError.get()}");
            return 1;
        }
        log.info(STR."Transcoding \{directory}, converting \{String.join(", ", parent.transcodeConfig.videoCodecs())}");
        try (val executorService = Executors.newScheduledThreadPool(10);
             val sessionFactory = PersistenceConfiguration.getSessionFactory(aniDbConfig.cache().db());
             val filesystem = new DoOnFileSystem()) {
            val aniAddO = parent.initializeAniAdd(true, executorService, filesystem, directory, sessionFactory);
            if (aniAddO.isEmpty()) {
                executorService.shutdownNow();
                return 1;
            }

            aniAddO.get().ProcessDirectory(directory);

            val _ = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        return 0;
    }

    public static String getName() {
        return CommandHelper.getName(TranscodeCommand.class);
    }
}

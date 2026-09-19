package startup.commands.anidb;

import cache.PersistenceConfiguration;
import config.blocks.AniDbConfig;
import config.blocks.TranscodeConfig;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import startup.commands.util.CommandHelper;
import startup.validation.validators.config.MapConfig;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Two jobs under one name, told apart by whether a directory is given.
 * <ul>
 *     <li>`anidb transcode`: the transcoder process. Works through the queue in the sqlite cache until stopped,
 *     encoding one file at a time. Meant to run as its own long-lived container next to the pipeline.</li>
 *     <li>`anidb transcode &lt;dir&gt;`: queues an existing library folder. Identical to `scan` except queueing
 *     is on whatever transcode.mode says, so pointing it at series/ needs no config edit. Encodes nothing.</li>
 * </ul>
 */
@Slf4j
@CommandLine.Command(name = "transcode", mixinStandardHelpOptions = true, version = "1.0",
        description = "Without a directory: work through the transcode queue until stopped. With a directory: identify its files and queue the matching ones.")
public class TranscodeCommand implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", arity = "0..1", description = "A directory to queue instead of running the transcoder.")
    private Path directory;

    @CommandLine.ParentCommand
    private AnidbCommand parent;

    @MapConfig(configPath = "anidb")
    private AniDbConfig aniDbConfig;

    @Override
    public Integer call() throws Exception {
        return directory == null ? runQueue() : queueDirectory();
    }

    private int runQueue() throws Exception {
        try (val sessionFactory = PersistenceConfiguration.getSessionFactory(aniDbConfig.cache().db())) {
            val runner = parent.initializeTranscodeRunner(sessionFactory);
            if (runner.isEmpty()) {
                return 1;
            }
            // docker stop sends SIGTERM: interrupt the runner so ffmpeg is killed and the job is released for
            // the next start, then wait for that before the JVM goes down.
            val worker = Thread.currentThread();
            val shutdownHook = new Thread(() -> {
                worker.interrupt();
                try {
                    worker.join(Duration.ofSeconds(30));
                } catch (InterruptedException ignored) {
                }
            }, "transcode-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            try {
                runner.get().run();
            } catch (InterruptedException e) {
                log.info("Transcoder stopped");
            } finally {
                // On a normal exit the hook must not wait for this thread, which is the one calling System.exit.
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException alreadyShuttingDown) {
                }
            }
        }
        return 0;
    }

    private int queueDirectory() throws Exception {
        parent.transcodeConfig.mode(TranscodeConfig.Mode.QUEUE);
        log.info(STR."Queueing matching files in \{directory} for transcoding");
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

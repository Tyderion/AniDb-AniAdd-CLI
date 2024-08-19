package startup.commands.anidb;

import cache.PersistenceConfiguration;
import lombok.val;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import startup.validation.validators.nonblank.NonBlank;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@CommandLine.Command(name = "scan", mixinStandardHelpOptions = true, version = "1.0",
        description = "Scans the directory for files and adds them to AniDb")
public class ScanCommand implements Callable<Integer> {
    @NonBlank
    @CommandLine.Parameters(index = "0", description = "The directory to scan.")
    private Path directory;

    @CommandLine.ParentCommand
    private AnidbCommand parent;

    @Override
    public Integer call() throws Exception {
        try (val executorService = Executors.newScheduledThreadPool(10);
             val sessionFactory = PersistenceConfiguration.getSessionFactory(parent.getDbPath());
             val filesystem = new DoOnFileSystem()) {
            val aniAddO = parent.initializeAniAdd(true, executorService,filesystem , directory, sessionFactory);
            if (aniAddO.isEmpty()) {
                executorService.shutdownNow();
                return 1;
            }

            val aniAdd = aniAddO.get();
            aniAdd.ProcessDirectory(directory);

            val _ = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        return 0;
    }
}

package aniAdd.startup.commands.anidb;

import aniAdd.startup.validation.validators.min.Min;
import aniAdd.startup.validation.validators.nonempty.NonEmpty;
import lombok.val;
import picocli.CommandLine;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@CommandLine.Command(name = "watch", mixinStandardHelpOptions = true, version = "1.0",
        description = "Periodically scans the directory for files and adds them to AniDb")
public class WatchCommand implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "The directory to scan.")
    @NonEmpty
    private String directory;

    @Min(value = 10, message = "Interval must be at least 10 minutes")
    @CommandLine.Option(names = {"-i", "--interval"}, description = "The interval in minutes to scan the directory", defaultValue = "30")
    private int interval;

    @CommandLine.ParentCommand
    private AnidbCommand parent;

    @Override
    public Integer call() throws Exception {
        try (val executorService = Executors.newScheduledThreadPool(10)) {
            val aniAdd = parent.initializeAniAdd(true, executorService);

            executorService.scheduleAtFixedRate(() -> aniAdd.ProcessDirectory(directory), 0, interval, TimeUnit.MINUTES);

            val _ = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        return 0;
    }
}

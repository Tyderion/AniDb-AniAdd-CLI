package startup.commands.anidb;

import cache.PersistenceConfiguration;
import config.blocks.AniDbConfig;
import lombok.val;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import startup.commands.util.CommandHelper;
import startup.validation.validators.config.MapConfig;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.port.Port;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@CommandLine.Command(name = "scan", mixinStandardHelpOptions = true, version = "1.0",
        description = "Scans the directory for files and adds them to AniDb")
public class ScanCommand implements Callable<Integer> {
    @NonBlank
    @CommandLine.Parameters(index = "0", description = "The directory to scan.")
    private Path directory;

    // Only used by kodi.libraryScan; connect-to-kodi and watch declare the same two options themselves.
    @Port
    @MapConfig(configPath = "kodi.port", envVariableName = "KODI_PORT")
    @CommandLine.Option(names = {"--kodi-port"}, description = "The kodi websocket port, used for the library scan")
    private Integer kodiPort;

    @NonBlank(allowNull = true)
    @MapConfig(configPath = "kodi.host", envVariableName = "KODI_HOST")
    @CommandLine.Option(names = {"--kodi-host"}, description = "The ip/hostname of the kodi server, used for the library scan")
    private String kodiHost;

    @CommandLine.ParentCommand
    private AnidbCommand parent;

    @MapConfig(configPath = "anidb")
    private AniDbConfig aniDbConfig;

    @Override
    public Integer call() throws Exception {
        try (val executorService = Executors.newScheduledThreadPool(10);
             val sessionFactory = PersistenceConfiguration.getSessionFactory(aniDbConfig.cache().db());
             val filesystem = new DoOnFileSystem()) {
            val aniAddO = parent.initializeAniAdd(true, executorService, filesystem, directory, sessionFactory);
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

    public static String getName() {
        return CommandHelper.getName(ScanCommand.class);
    }
}

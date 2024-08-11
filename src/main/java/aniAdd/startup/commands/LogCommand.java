package aniAdd.startup.commands;

import cache.AniDBFileRepository;
import cache.PersistenceConfiguration;
import cache.entities.AniDBFileData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

@Slf4j
@CommandLine.Command(name = "log", mixinStandardHelpOptions = true, version = "1.0", description = "Log handling")
public class LogCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        try (val executor = Executors.newScheduledThreadPool(5); val sessionFactory = PersistenceConfiguration.getSessionFactory("test.sqlite")) {
            log.info("INfo Log application");
            log.trace("Trace log application");
            val fileRepository = new AniDBFileRepository(sessionFactory);
            fileRepository.saveAniDBFileData(AniDBFileData.builder()
                    .size(1000)
                            .fileName("asdf")
                            .folderName("asdf")
                    .ed2k("asdfasdf")
                    .build());
//            new DownloadHelper(executor).downloadToFile(
//                    "https://raw.githubusercontent.com/Tyderion/AniDb-AniAdd-CLI/feature/kodi-nfo/src/main/java/aniAdd/startup/commands/anidb/AnidbCommand.java",
//                    Path.of("test.java"));
        }
        System.out.println("Log handling");
        return 0;
    }
}

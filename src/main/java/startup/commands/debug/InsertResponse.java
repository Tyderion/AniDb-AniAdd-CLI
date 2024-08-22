package startup.commands.debug;

import cache.AniDBFileRepository;
import cache.PersistenceConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import processing.FileInfo;
import processing.tagsystem.TagSystemTags;
import udpapi.ParseReply;
import udpapi.command.FileCommand;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

@Slf4j
@CommandLine.Command(name = "insert-response",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Insert Reply from message")
public class InsertResponse implements Callable<Integer> {
    @CommandLine.Option(names = "--message", description = "The message to parse", required = false, defaultValue = "file:407-408 220 FILE\n3243049|9368|141493|14730|0||0|0|bdf45300||high|DVD|AC3|H264/AVC|848x480|japanese|english|1415|1354579200|Nurarihyon no Mago (2012) - 1 - Zero\n            Tears Snow - [PWNED](bdf45300).mkv||2|2|2012-2013|OVA|Manga,Shounen|Nurarihyon no Mago (2012)|???????? (2012)||???????? (2012)|1|Zero Tears Snow|Rei: Namida - Yuki|?????|pwnedbygary|PWNED\n")
    private String message;

    @CommandLine.Option(names = "--ed2k", description = "The ed2k hash to use", required = false, defaultValue = "057ec5dba1ea40c1e82e24a0f70405d5")
    private String ed2k;

    @CommandLine.Option(names = "--size", description = "The file size to use", required = false, defaultValue = "524504029")
    private int fileSize;

    @CommandLine.Option(names = "--filepath", description = "The file path to use", required = false, defaultValue = "Z:\\2-WorkingImport\\tvdb\\Nura - Rise of the Yokai Clan [tvdb-171731]\\Nura - Rise of the Yokai Clan - S00E06 - Zero Tears Snow.mkv")
    private String filePath;

    @CommandLine.ParentCommand
    DebugCommand parent;

    @Override
    public Integer call() throws Exception {
        try (val executorService = Executors.newScheduledThreadPool(10)) {
            executorService.execute(new ParseReply(reply -> {
                if (reply == null || !reply.getReplyStatus().success()) {
                    log.error(STR."Cannot insert data for non successful file command: \{reply}");
                }
                try (val sessionFactory = PersistenceConfiguration.getSessionFactory(parent.getDbPath())) {
                    val repository = new AniDBFileRepository(sessionFactory);
                    val info = new FileInfo(new FakeFile(Path.of(filePath), fileSize, true), 1, null, null);
                    info.getData().put(TagSystemTags.Ed2kHash, ed2k);
                    FileCommand.AddReplyToDict(info.getData(), reply, false);
                    repository.saveAniDBFileData(info.toAniDBFileData());
                }

            }, message));
        }
        return 0;
    }

}

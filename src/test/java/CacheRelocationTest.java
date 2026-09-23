import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import config.RootConfiguration;
import config.RunConfig;
import startup.commands.anidb.AnidbCommand;
import utils.config.ConfigFileHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A relative cache path used to mean relative to the working directory and now means relative to the config
 * file. An existing config can therefore switch silently to a new, empty cache, which looks every file up
 * again and risks the AniDB account. The command refuses in exactly that situation and no other.
 */
public class CacheRelocationTest {

    @TempDir
    Path tempDir;

    Path configDir;
    Path workingDir;
    Path config;

    @BeforeEach
    void layout() throws IOException {
        val root = tempDir.toRealPath();
        configDir = Files.createDirectories(root.resolve("config"));
        workingDir = Files.createDirectories(root.resolve("work"));
        config = Files.writeString(configDir.resolve("settings.yaml"), "anidb:\n  cache:\n    db: aniAdd.sqlite\n");
    }

    @Test
    public void refusesWhenTheCacheOnlyExistsWhereTheOldRulePutIt() throws IOException {
        Files.writeString(workingDir.resolve("aniAdd.sqlite"), "cache");

        val problem = AnidbCommand.cacheRelocationProblem("aniAdd.sqlite", config, workingDir);

        assertThat(problem, notNullValue());
        assertThat(problem, containsString(configDir.resolve("aniAdd.sqlite").toString()));
        assertThat("names the line that keeps the old cache", problem, containsString("db: " + workingDir.resolve("aniAdd.sqlite")));
    }

    @Test
    public void aFreshSetupWhereNeitherExistsStartsNormally() {
        assertThat(AnidbCommand.cacheRelocationProblem("aniAdd.sqlite", config, workingDir), is(nullValue()));
    }

    @Test
    public void aCacheAlreadyAtTheNewLocationIsFine() throws IOException {
        Files.writeString(workingDir.resolve("aniAdd.sqlite"), "old");
        Files.writeString(configDir.resolve("aniAdd.sqlite"), "new");

        assertThat(AnidbCommand.cacheRelocationProblem("aniAdd.sqlite", config, workingDir), is(nullValue()));
    }

    @Test
    public void runningFromTheConfigDirectoryMeansNothingMoved() throws IOException {
        Files.writeString(configDir.resolve("aniAdd.sqlite"), "cache");

        assertThat(AnidbCommand.cacheRelocationProblem("aniAdd.sqlite", config, configDir), is(nullValue()));
    }

    /**
     * A relative db in a run file's args moved the same way, but RunConfig turns it into an explicit --db, which
     * the check in AnidbCommand trusts. So RunConfig applies the rule itself, on the value as written. The rule
     * compares against the working directory, which a test cannot change, so the old cache is placed there
     * under a unique name and removed afterwards.
     */
    @Test
    public void aMovedCacheInRunArgsIsRefusedToo() throws Exception {
        val name = "relocation-probe-" + java.util.UUID.randomUUID() + ".sqlite";
        val oldCache = Path.of("").toAbsolutePath().resolve(name);
        val runFile = Files.writeString(configDir.resolve("run.yaml"), """
                run:
                  task: scan
                  args:
                    path: /media/input
                    db: %s
                """.formatted(name));
        Files.writeString(oldCache, "cache");
        try {
            RunConfig run = new ConfigFileHandler<>(RootConfiguration.class).getConfiguration(runFile).run();
            val refused = assertThrows(RunConfig.InvalidConfigException.class, () -> run.toCommandArgs(runFile));
            assertThat(refused.getMessage(), containsString("db: " + oldCache));
        } finally {
            Files.deleteIfExists(oldCache);
        }
    }

    @Test
    public void absoluteAndMissingValuesAreNotItsBusiness() throws IOException {
        Files.writeString(workingDir.resolve("aniAdd.sqlite"), "cache");

        assertThat(AnidbCommand.cacheRelocationProblem("/var/lib/aniAdd.sqlite", config, workingDir), is(nullValue()));
        assertThat(AnidbCommand.cacheRelocationProblem(null, config, workingDir), is(nullValue()));
        assertThat(AnidbCommand.cacheRelocationProblem("  ", config, workingDir), is(nullValue()));
    }
}

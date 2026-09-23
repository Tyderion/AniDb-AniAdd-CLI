import config.RootConfiguration;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import utils.config.ConfigFileHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A relative path in a config file means relative to that file, the way compose files and tsconfig behave.
 * Before this, paths resolved against the working directory, so the same config did different things
 * depending on where it was launched from, and anything shared between checkouts had to be absolute.
 */
public class PathResolutionTest {

    @Test
    public void aRelativeCachePathResolvesNextToTheConfigFile(@TempDir Path dir) throws IOException {
        val config = write(dir.resolve("settings.yaml"), """
                anidb:
                  cache:
                    db: cache/aniAdd.sqlite
                """);
        assertThat(config.anidb().cache().db(), is(dir.resolve("cache/aniAdd.sqlite")));
    }

    @Test
    public void aRelativePathCanClimbOutOfTheConfigDirectory(@TempDir Path dir) throws IOException {
        val nested = Files.createDirectories(dir.resolve("worktree"));
        val config = write(nested.resolve("settings.yaml"), """
                anidb:
                  cache:
                    db: ../aniAdd.sqlite
                """);
        assertThat(config.anidb().cache().db(), is(dir.resolve("aniAdd.sqlite")));
    }

    @Test
    public void anAbsolutePathIsLeftAlone(@TempDir Path dir) throws IOException {
        val config = write(dir.resolve("settings.yaml"), """
                anidb:
                  cache:
                    db: /var/lib/aniadd/aniAdd.sqlite
                """);
        assertThat(config.anidb().cache().db(), is(Path.of("/var/lib/aniadd/aniAdd.sqlite")));
    }

    @Test
    public void moveAndTagPathsFollowTheSameRule(@TempDir Path dir) throws IOException {
        val config = write(dir.resolve("settings.yaml"), """
                file:
                  move:
                    unknown:
                      folder: unknown/
                tags:
                  paths:
                    movieFolders:
                      - path: output/movies/
                        tagSystemName: BaseMoviePath
                """);
        assertThat(config.file().move().unknown().folder(), is(dir.resolve("unknown")));
        assertThat(config.tags().paths().movieFolders().get(0).path(), is(dir.resolve("output/movies")));
    }

    @Test
    public void theScanPathResolvesAgainstTheRunFile(@TempDir Path dir) throws Exception {
        val runFile = dir.resolve("run.yaml");
        val config = write(runFile, """
                run:
                  task: scan
                  args:
                    path: sandbox/input/
                """);
        val args = config.run().toCommandArgs(runFile);
        assertThat(args, hasItem(dir.resolve("sandbox/input").toString()));
    }

    /**
     * The regression that motivated this: a relative --config used to make the delegated config path pass
     * through untouched, so it resolved against the working directory instead of next to the run file.
     */
    @Test
    public void theDelegatedConfigResolvesAgainstTheRunFileEvenWhenInvokedRelatively(@TempDir Path dir) throws Exception {
        val runFile = dir.resolve("run.yaml");
        val config = write(runFile, """
                run:
                  task: scan
                  args:
                    path: /media/input
                  config: settings.yaml
                """);
        val relativeInvocation = Path.of("").toAbsolutePath().relativize(runFile.toAbsolutePath());
        val args = config.run().toCommandArgs(relativeInvocation);
        assertThat(args, hasItem(STR."--config=\{dir.resolve("settings.yaml")}"));
    }

    /**
     * db is a string in the args map rather than a Path, so it never reaches the parser's path handling.
     * Without explicit resolution it would follow the working directory, and the same config would use a
     * different cache from the IDE than from a shell.
     */
    @Test
    public void aCachePathInArgsResolvesAgainstTheRunFile(@TempDir Path dir) throws Exception {
        val runFile = dir.resolve("run.yaml");
        val config = write(runFile, """
                run:
                  task: scan
                  args:
                    path: /media/input
                    db: cache/aniAdd.sqlite
                """);
        assertThat(config.run().toCommandArgs(runFile), hasItem(STR."--db=\{dir.resolve("cache/aniAdd.sqlite")}"));
    }

    @Test
    public void anAbsoluteDelegatedConfigIsLeftAlone(@TempDir Path dir) throws Exception {
        val runFile = dir.resolve("run.yaml");
        val config = write(runFile, """
                run:
                  task: scan
                  args:
                    path: /media/input
                  config: /etc/aniadd/settings.yaml
                """);
        assertThat(config.run().toCommandArgs(runFile), hasItem("--config=/etc/aniadd/settings.yaml"));
    }

    /**
     * The shared setup links one config into every checkout, so the link must behave like the file it points
     * at. Resolving against the link's own directory would send every path into the wrong checkout.
     */
    @Test
    public void aSymlinkedConfigResolvesAgainstTheRealFile(@TempDir Path dir) throws Exception {
        val real = Files.createDirectories(dir.resolve("sandbox")).resolve("sandbox.yaml");
        write(real, """
                run:
                  task: scan
                  args:
                    path: input/
                anidb:
                  cache:
                    db: ../aniAdd.sqlite
                """);
        val worktree = Files.createDirectories(dir.resolve("worktree"));
        val link = Files.createSymbolicLink(worktree.resolve("sandbox.yaml"), Path.of("../sandbox/sandbox.yaml"));

        val config = new ConfigFileHandler<>(RootConfiguration.class).getConfiguration(link);
        assertNotNull(config);
        assertThat(config.anidb().cache().db(), is(dir.resolve("aniAdd.sqlite")));
        assertThat(config.run().toCommandArgs(link), hasItem(dir.resolve("sandbox/input").toString()));
    }

    private RootConfiguration write(Path file, String yaml) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml);
        val config = new ConfigFileHandler<>(RootConfiguration.class).getConfiguration(file);
        assertNotNull(config, STR."failed to load \{file}");
        return config;
    }
}

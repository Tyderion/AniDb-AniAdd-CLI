import config.RootConfiguration;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;
import utils.config.ConfigFileHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The configs in .run/ are tracked, so they are the ones people actually run and the ones worth testing.
 * Loading them here catches a broken path or a dropped safety gate at build time rather than halfway
 * through a run against real files.
 */
public class SandboxConfigTest {

    private static final Path RUN = Path.of(".run");
    private static final Path REPO = Path.of("").toAbsolutePath();

    @Test
    public void theSandboxSettingsResolveInsideTheSandbox() {
        // The cache is deliberately not in sandbox/: it sits beside the checkout so the sandbox reuses
        // lookups from real runs. everyConfigKeepsItsCacheInsideTheCheckout covers it.
        val config = load(RUN.resolve("sandbox.yaml"));
        assertThat(config.file().move().unknown().folder(), is(REPO.resolve("sandbox/unknown")));
        assertThat(config.file().move().duplicates().folder(), is(REPO.resolve("sandbox/duplicates")));
        assertThat(config.tags().paths().movieFolders().get(0).path(), is(REPO.resolve("sandbox/output/movies")));
        assertThat(config.tags().paths().tvShowFolders().get(0).path(), is(REPO.resolve("sandbox/output/series")));
    }

    @Test
    public void theSandboxSettingsKeepTheirSafetyGates() {
        val config = load(RUN.resolve("sandbox.yaml"));
        assertThat("moves are confined to the sandbox", config.file().move().confineTo(), is(REPO.resolve("sandbox")));
        assertThat(config.file().mylist().add(), is(false));
        assertThat(config.anidb().exitOnBan(), is(true));
    }

    @Test
    public void theSandboxSettingsCarryNoRunBlock() {
        // Entry points live in the sandbox-*.yaml files, so there is exactly one way to start each task.
        assertThat(load(RUN.resolve("sandbox.yaml")).run(), is(nullValue()));
    }

    @Test
    public void theSettingsFilesShareOneTagSystem() {
        val resolved = java.util.stream.Stream.of("sandbox.yaml", "scan-local.yaml", "scan-inplace.yaml")
                .map(file -> {
                    val tagSystem = load(RUN.resolve(file)).tags().tagSystem();
                    assertNotNull(tagSystem, STR."\{file} resolved no tag system");
                    return tagSystem;
                })
                .distinct()
                .toList();
        assertThat("the three settings files disagree about the tag system", resolved.size(), is(1));
        // Comparing them only with the file they name would pass on an empty or truncated one.
        assertThat(resolved.get(0), containsString("FileName:="));
        assertThat(resolved.get(0).lines().count() > 20, is(true));
    }

    @ParameterizedTest
    @CsvSource({
            "sandbox-scan.yaml,       scan,            true",
            "sandbox-watch.yaml,      watch,           true",
            "sandbox-watch-kodi.yaml, watch,           true",
            "sandbox-kodi.yaml,       connect-to-kodi, false",
    })
    public void everyRunFileDrivesTheSandboxSettings(String file, String command, boolean usesInput) throws Exception {
        val runFile = RUN.resolve(file);
        val config = load(runFile);
        assertNotNull(config.run(), STR."\{file} has no run block");

        val args = config.run().toCommandArgs(runFile);
        assertThat(args, hasItem(command));
        assertThat(args, hasItem(STR."--config=\{REPO.resolve(".run/sandbox.yaml")}"));
        if (usesInput) {
            assertThat(args, hasItem(REPO.resolve("sandbox/input").toString()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sandbox-scan.yaml", "sandbox-watch.yaml", "sandbox-watch-kodi.yaml", "sandbox-kodi.yaml"})
    public void noRunFileCarriesItsOwnSettings(String file) throws IOException {
        // A run file that redefined settings would silently diverge from the shared sandbox.
        val keys = raw(RUN.resolve(file)).keySet();
        assertThat(keys, not(hasItem("anidb")));
        assertThat(keys, not(hasItem("file")));
        assertThat(keys, not(hasItem("tags")));
    }

    @ParameterizedTest
    @CsvSource({
            "docker-scan.yaml,       scan,            true",
            "docker-watch.yaml,      watch,           true",
            "docker-watch-kodi.yaml, watch,           true",
            "docker-kodi.yaml,       connect-to-kodi, false",
    })
    public void everyDockerRunFileFindsItsSettingsFile(String file, String command, boolean usesInput) throws Exception {
        // These delegate to config/docker.yaml. A reference that does not resolve is only discovered when
        // someone mounts the file into a container and the run dies there.
        val runFile = RUN.resolve(file);
        val config = load(runFile);
        val args = config.run().toCommandArgs(runFile);
        assertThat(args, hasItem(command));
        assertThat(args, hasItem(STR."--config=\{REPO.resolve("config/docker.yaml")}"));
        assertThat(Files.exists(REPO.resolve("config/docker.yaml")), is(true));
        if (usesInput) {
            assertThat(args, hasItem("/from"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sandbox.yaml", "sandbox-scan.yaml", "sandbox-watch.yaml", "sandbox-watch-kodi.yaml",
            "sandbox-kodi.yaml", "scan-local.yaml", "scan-inplace.yaml", "docker-scan.yaml", "docker-watch.yaml",
            "docker-watch-kodi.yaml", "docker-kodi.yaml"})
    public void noTrackedConfigNamesAHomeDirectory(String file) throws IOException {
        // These files are shared, so a path under someone's home is both wrong for everyone else and a
        // small disclosure of how one machine is laid out. The real location lives in an untracked link.
        assertThat(Files.readString(RUN.resolve(file)), not(containsString("/home/")));
    }

    @Test
    public void theLocalScanConfigsReachTheLibraryThroughTheLink() {
        for (String file : new String[]{"scan-local.yaml", "scan-inplace.yaml"}) {
            val config = load(RUN.resolve(file));
            assertThat(STR."\{file} movie output", config.tags().paths().movieFolders().get(0).path(),
                    is(REPO.resolve("library/output/movies")));
        }
    }

    /**
     * Inside the checkout, not above it. A path climbing out of the project put a database in the clone's
     * parent directory, which is nobody's idea of where a cache goes.
     */
    @Test
    public void everyConfigKeepsItsCacheInsideTheCheckout() {
        for (String file : new String[]{"sandbox.yaml", "scan-local.yaml", "scan-inplace.yaml"}) {
            val db = load(RUN.resolve(file)).anidb().cache().db();
            assertThat(STR."\{file} cache", db, is(REPO.resolve("aniAdd.sqlite")));
        }
    }

    private RootConfiguration load(Path file) {
        val config = new ConfigFileHandler<>(RootConfiguration.class).getConfiguration(file);
        assertNotNull(config, STR."failed to load \{file}");
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> raw(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return (Map<String, Object>) new Yaml().load(input);
        }
    }
}

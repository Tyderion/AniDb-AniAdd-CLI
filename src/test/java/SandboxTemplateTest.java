import config.RootConfiguration;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.yaml.snakeyaml.Yaml;
import utils.config.ConfigFileHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The sandboxInit task builds sandbox/ from tracked templates: sandbox.yaml by merging
 * gradle/sandbox-overrides.yaml onto .run/scan-local.yaml, and the run files by copying
 * gradle/sandbox-runs/. Nothing else checks the result is something this application can load, and the
 * failure would otherwise surface halfway through a run. These tests build the same tree in a temp
 * directory and load it the way the CLI would.
 */
public class SandboxTemplateTest {

    private static final Path BASE = Path.of(".run", "scan-local.yaml");
    private static final Path OVERRIDES = Path.of("gradle", "sandbox-overrides.yaml");
    private static final Path RUN_TEMPLATES = Path.of("gradle", "sandbox-runs");

    @Test
    public void everySandboxPathLandsInsideTheContainer(@TempDir Path container) throws Exception {
        val config = buildSandbox(container);
        assertThat(config.anidb().cache().db(), is(container.resolve("aniAdd.sqlite")));
        assertThat(config.file().move().unknown().folder(), is(container.resolve("sandbox/unknown")));
        assertThat(config.file().move().duplicates().folder(), is(container.resolve("sandbox/duplicates")));
        assertThat(config.tags().paths().movieFolders().get(0).path(), is(container.resolve("sandbox/output/movies")));
        assertThat(config.tags().paths().tvShowFolders().get(0).path(), is(container.resolve("sandbox/output/series")));
    }

    @Test
    public void theSafetyGatesSurviveTheMerge(@TempDir Path container) throws Exception {
        val config = buildSandbox(container);
        assertThat(config.file().mylist().add(), is(false));
        assertThat(config.anidb().exitOnBan(), is(true));
    }

    @Test
    public void theSettingsFileCarriesNoRunBlock(@TempDir Path container) throws Exception {
        assertThat(buildSandbox(container).run(), is((Object) null));
    }

    @Test
    public void theTagSystemComesFromTheBaseConfigRatherThanBeingDuplicated(@TempDir Path container) throws Exception {
        val overrides = load(OVERRIDES);
        assertThat(((Map<?, ?>) overrides.get("tags")).containsKey("tagSystem"), is(false));
        assertNotNull(buildSandbox(container).tags().tagSystem(), "tag system lost in the merge");
    }

    @ParameterizedTest
    @CsvSource({
            "sandbox-scan.yaml,        scan,  true",
            "sandbox-watch.yaml,       watch, true",
            "sandbox-watch-kodi.yaml,  watch, true",
            "sandbox-kodi.yaml,        connect-to-kodi, false",
    })
    public void everyRunFileProducesACommandAgainstTheSandbox(String file, String command, boolean usesInput,
                                                             @TempDir Path container) throws Exception {
        buildSandbox(container);
        val runFile = container.resolve("sandbox").resolve(file);
        val config = new ConfigFileHandler<>(RootConfiguration.class).getConfiguration(runFile);
        assertNotNull(config, STR."\{file} did not load");
        assertNotNull(config.run(), STR."\{file} has no run block");

        val args = config.run().toCommandArgs(runFile);
        assertThat(args, hasItem(command));
        assertThat(args, hasItem(STR."--config=\{container.resolve("sandbox/sandbox.yaml")}"));
        if (usesInput) {
            assertThat(args, hasItem(container.resolve("sandbox/input").toString()));
        }
    }

    @ParameterizedTest
    @CsvSource({"sandbox-scan.yaml", "sandbox-watch.yaml", "sandbox-watch-kodi.yaml", "sandbox-kodi.yaml"})
    public void noRunFileCarriesItsOwnSettings(String file) throws IOException {
        // A run file that redefined settings would silently diverge from the shared sandbox.
        val keys = load(RUN_TEMPLATES.resolve(file)).keySet();
        assertThat(keys, not(hasItem("anidb")));
        assertThat(keys, not(hasItem("file")));
        assertThat(keys, not(hasItem("tags")));
    }

    /** Mirrors sandboxInit, so a broken template fails here rather than mid-run. */
    private RootConfiguration buildSandbox(Path container) throws IOException {
        val sandbox = Files.createDirectories(container.resolve("sandbox"));
        val settings = sandbox.resolve("sandbox.yaml");
        val merged = new LinkedHashMap<>(deepMerge(load(BASE), load(OVERRIDES)));
        merged.remove("run");
        Files.writeString(settings, new Yaml().dump(merged));
        try (Stream<Path> templates = Files.list(RUN_TEMPLATES)) {
            for (Path template : templates.toList()) {
                Files.copy(template, sandbox.resolve(template.getFileName()));
            }
        }
        val config = new ConfigFileHandler<>(RootConfiguration.class).getConfiguration(settings);
        assertNotNull(config, "generated sandbox config did not load");
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> load(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return (Map<String, Object>) new Yaml().load(input);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> override) {
        val merged = new LinkedHashMap<>(base);
        override.forEach((key, value) -> {
            val existing = merged.get(key);
            if (existing instanceof Map && value instanceof Map) {
                merged.put(key, deepMerge((Map<String, Object>) existing, (Map<String, Object>) value));
            } else {
                merged.put(key, value);
            }
        });
        return merged;
    }
}

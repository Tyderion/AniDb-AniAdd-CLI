import config.RootConfiguration;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import utils.config.ConfigFileParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * gradle/sandbox-overrides.yaml is merged onto .run/scan-local.yaml by the sandboxInit task to produce
 * the local sandbox config. Nothing else checks that the result is something this application can
 * actually load, and the failure would otherwise surface halfway through a run. This repeats the
 * task's merge against the tracked files so a broken template fails the build instead.
 */
public class SandboxTemplateTest {

    private static final Path BASE = Path.of(".run", "scan-local.yaml");
    private static final Path OVERRIDES = Path.of("gradle", "sandbox-overrides.yaml");

    @Test
    public void theGeneratedSandboxConfigIsLoadableAndDrivesAScan() throws Exception {
        val parsed = parse(render());
        assertNotNull(parsed.run(), "no run block");
        assertThat(parsed.run().toCommandArgs(BASE.toAbsolutePath()), hasItem("scan"));
    }

    @Test
    public void theSafetyGatesSurviveTheMerge() throws Exception {
        val parsed = parse(render());
        assertThat(parsed.file().mylist().add(), is(false));
        assertThat(parsed.anidb().exitOnBan(), is(true));
        assertThat(parsed.anidb().cache().db().toString(), is("/tmp/container/aniAdd.sqlite"));
    }

    @Test
    public void theTagSystemComesFromTheBaseConfigRatherThanBeingDuplicated() throws Exception {
        val overrides = load(OVERRIDES);
        assertThat(((Map<?, ?>) overrides.get("tags")).containsKey("tagSystem"), is(false));
        assertNotNull(parse(render()).tags().tagSystem(), "tag system lost in the merge");
    }

    private String render() throws IOException {
        val merged = deepMerge(load(BASE), load(OVERRIDES));
        val yaml = new Yaml().dump(merged);
        return yaml.replace("@ROOT@", "/tmp/container").replace("@SANDBOX@", "/tmp/container/sandbox");
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

    private RootConfiguration parse(String yaml) {
        val parsed = new ConfigFileParser<>(RootConfiguration.class)
                .load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(parsed);
        return parsed;
    }
}

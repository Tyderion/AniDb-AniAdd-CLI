import config.RootConfiguration;
import lombok.val;
import org.junit.jupiter.api.Test;
import utils.config.ConfigFileParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A config written by a newer branch must still load on an older one: keys this branch does not know
 * are skipped rather than throwing. Without this, every worktree would need its own copy of the shared
 * run configs in .run/.
 */
public class ConfigForwardCompatibilityTest {

    @Test
    public void ignoresAnUnknownTopLevelBlock() {
        val parsed = parse("""
                anidb:
                  localPort: 3333
                transcode:
                  enabled: true
                  outputFormat: x265
                  crf: 24
                """);
        assertThat(parsed.anidb().localPort(), is(3333));
    }

    @Test
    public void ignoresUnknownKeysInsideAKnownBlock() {
        val parsed = parse("""
                anidb:
                  localPort: 3333
                  someKeyFromTheFuture: whatever
                kodi:
                  port: 9090
                  transcodeAware: true
                """);
        assertThat(parsed.anidb().localPort(), is(3333));
        assertThat(parsed.kodi().port(), is(9090));
    }

    @Test
    public void loadsTheSharedScanLocalConfig() throws Exception {
        assertSharedRunConfigIsUsable(Path.of(".run", "scan-local.yaml"));
    }

    @Test
    public void loadsTheSharedScanInplaceConfig() throws Exception {
        assertSharedRunConfigIsUsable(Path.of(".run", "scan-inplace.yaml"));
    }

    /**
     * Parsing alone would pass on a file the CLI then refuses, so go all the way to the argument list.
     */
    private void assertSharedRunConfigIsUsable(Path path) throws Exception {
        val parsed = parseFile(path);
        assertNotNull(parsed.run(), STR."no run block in \{path}");
        val args = parsed.run().toCommandArgs(path.toAbsolutePath());
        assertThat(args, hasItem("scan"));
        assertThat(parsed.anidb().cache().db().toString(), containsString("aniAdd.sqlite"));
    }

    private RootConfiguration parseFile(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            val parsed = new ConfigFileParser<>(RootConfiguration.class).load(input);
            assertNotNull(parsed, STR."failed to parse \{path}");
            return parsed;
        }
    }

    private RootConfiguration parse(String yaml) {
        val parsed = new ConfigFileParser<>(RootConfiguration.class).load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(parsed);
        return parsed;
    }
}

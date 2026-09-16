import config.RootConfiguration;
import config.blocks.KodiLibraryScanConfig;
import lombok.val;
import org.junit.jupiter.api.Test;
import utils.config.ConfigFileParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KodiLibraryScanConfigTest {

    @Test
    public void isOffAndValidWhenTheBlockIsMissing() {
        val scan = parse("""
                kodi:
                  host: kodi.local
                """).kodi().libraryScan();
        assertNotNull(scan);
        assertThat(scan.enabled(), is(false));
        assertThat(scan.isDefault(), is(true));
        assertThat(scan.problems(), empty());
    }

    @Test
    public void readsLibrariesByNameAndByPath() {
        val scan = parse("""
                kodi:
                  libraryScan:
                    enabled: true
                    scope: library
                    showDialogs: true
                    timeoutInMinutes: 5
                    libraries:
                      - name: anime series
                        localPath: /shows
                      - path: /storage/media/anime/movies/
                """).kodi().libraryScan();
        assertThat(scan.scope(), is(KodiLibraryScanConfig.Scope.LIBRARY));
        assertThat(scan.showDialogs(), is(true));
        assertThat(scan.timeoutInMinutes(), is(5));
        assertThat(scan.libraries(), hasSize(2));
        assertThat(scan.libraries().get(0).name(), is("anime series"));
        assertThat(scan.libraries().get(0).localPath(), is(Path.of("/shows")));
        assertThat(scan.libraries().get(1).path(), is("/storage/media/anime/movies/"));
        assertThat(scan.problems(), empty());
    }

    @Test
    public void reportsEnabledWithoutLibrariesAndAmbiguousEntries() {
        assertThat(parse("""
                kodi:
                  libraryScan:
                    enabled: true
                """).kodi().libraryScan().problems(), hasSize(1));

        val problems = parse("""
                kodi:
                  libraryScan:
                    enabled: true
                    libraries:
                      - name: anime series
                        path: /storage/media/anime/series/
                      - localPath: /shows
                """).kodi().libraryScan().problems();
        assertThat(problems, contains(containsString("libraries[0]"), containsString("libraries[1]")));
    }

    private RootConfiguration parse(String yaml) {
        val parsed = new ConfigFileParser<>(RootConfiguration.class).load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(parsed);
        return parsed;
    }
}

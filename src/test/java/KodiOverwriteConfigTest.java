import config.RootConfiguration;
import lombok.val;
import org.junit.jupiter.api.Test;
import utils.config.ConfigFileParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KodiOverwriteConfigTest {

    @Test
    public void overwritesNothingWhenTheBlockIsMissing() {
        val overwrite = parse("""
                kodi:
                  metadata:
                    generate: true
                """).kodi().metadata().overwrite();
        assertNotNull(overwrite);
        assertThat(overwrite.isDefault(), is(true));
    }

    @Test
    public void readsEachCaseSeparately() {
        val overwrite = parse("""
                kodi:
                  metadata:
                    overwrite:
                      series: true
                      artwork: true
                """).kodi().metadata().overwrite();
        assertThat(overwrite.series(), is(true));
        assertThat(overwrite.episodes(), is(false));
        assertThat(overwrite.movies(), is(false));
        assertThat(overwrite.artwork(), is(true));
    }

    private RootConfiguration parse(String yaml) {
        val parsed = new ConfigFileParser<>(RootConfiguration.class).load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(parsed);
        return parsed;
    }
}

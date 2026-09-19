import config.RootConfiguration;
import config.blocks.TranscodeConfig;
import lombok.val;
import org.junit.jupiter.api.Test;
import utils.config.ConfigFileParser;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TranscodeConfigTest {

    @Test
    public void theReferenceConfigParses() throws Exception {
        try (val input = new FileInputStream("config/docker.yaml")) {
            val transcode = new ConfigFileParser<>(RootConfiguration.class).load(input).transcode();
            assertThat(transcode.mode(), is(TranscodeConfig.Mode.OFF));
            assertThat(transcode.match().videoCodecs(), is(List.of("h264")));
            assertThat(transcode.nfo(), is(TranscodeConfig.Nfo.PATCH));
        }
    }

    @Test
    public void defaultsApplyWhenTheBlockIsSparse() {
        val transcode = parse("""
                transcode:
                  mode: queue
                  match:
                    profiles:
                      - High 10
                """).transcode();
        assertNotNull(transcode);
        assertThat(transcode.mode(), is(TranscodeConfig.Mode.QUEUE));
        assertThat(transcode.match().videoCodecs(), is(List.of("h264")));
        assertThat(transcode.match().profiles(), is(List.of("High 10")));
        assertThat(transcode.existing(), is(TranscodeConfig.Existing.SKIP));
        assertThat(transcode.nfo(), is(TranscodeConfig.Nfo.PATCH));
        assertThat(transcode.maxAttempts(), is(2));
        assertThat(transcode.pollSeconds(), is(60));
    }

    private RootConfiguration parse(String yaml) {
        return new ConfigFileParser<>(RootConfiguration.class).load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}

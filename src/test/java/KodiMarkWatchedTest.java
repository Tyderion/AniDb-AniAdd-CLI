import aniAdd.AniAdd;
import config.blocks.FileConfig;
import config.blocks.KodiConfig;
import config.blocks.MyListConfig;
import config.RootConfiguration;
import fileprocessor.FileProcessor;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import processing.EpisodeProcessing;
import udpapi.UdpApi;
import utils.config.ConfigFileHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static utils.config.ConfigFileParser.baseDirectoryOf;

/**
 * A play reported by Kodi is written back to MyList unless kodi.markWatched turns it off. It is a key of
 * its own because file.mylist.add cannot express it: that means "add the files I am scanning", and a
 * deployment that disables it still wants its watched state synced. Without the separate key, a sandbox
 * run against a real Kodi writes to the real account while every documented gate reads as closed.
 */
public class KodiMarkWatchedTest {

    @Test
    public void aPlayIsWrittenToMyListByDefault() {
        val processor = markWatchedWith(KodiConfig.builder().build());
        verify(processor).AddFile(any(Path.class), any(FileConfig.class));
    }

    @Test
    public void markWatchedOffWritesNothing() {
        val processor = markWatchedWith(KodiConfig.builder().markWatched(false).build());
        verify(processor, never()).AddFile(any(Path.class), any(FileConfig.class));
    }

    /**
     * The regression this guards: a deployment that stops scans touching MyList must still sync watches.
     */
    @Test
    public void aDeploymentThatDisablesMylistAddStillSyncsWatches() {
        val scansDoNotAdd = FileConfig.builder()
                .mylist(MyListConfig.builder().add(false).overwrite(false).build())
                .build();
        val processor = mock(FileProcessor.class);
        aniAdd(processor, scansDoNotAdd, KodiConfig.builder().build()).MarkFileAsWatched(Path.of("/tmp/x.mkv"));
        verify(processor).AddFile(any(Path.class), any(FileConfig.class));
    }

    @Test
    public void theDefaultIsOnSoExistingConfigsBehaveAsBefore() {
        assertThat(KodiConfig.builder().build().markWatched(), is(true));
        assertThat(parse("kodi:\n  host: kodi.local\n").kodi().markWatched(), is(true));
    }

    @Test
    public void theSandboxConfigTurnsItOff() throws IOException {
        val sandbox = new ConfigFileHandler<>(RootConfiguration.class)
                .getConfiguration(Path.of(".run", "sandbox.yaml"));
        assertThat(sandbox.kodi().markWatched(), is(false));
    }

    private FileProcessor markWatchedWith(KodiConfig kodiConfig) {
        val processor = mock(FileProcessor.class);
        aniAdd(processor, FileConfig.builder().build(), kodiConfig).MarkFileAsWatched(Path.of("/tmp/x.mkv"));
        return processor;
    }

    private AniAdd aniAdd(FileProcessor processor, FileConfig fileConfig, KodiConfig kodiConfig) {
        return new AniAdd(mock(UdpApi.class), false, processor, mock(EpisodeProcessing.class),
                _ -> { }, fileConfig, kodiConfig);
    }

    private RootConfiguration parse(String yaml) {
        return new utils.config.ConfigFileParser<>(RootConfiguration.class)
                .load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}

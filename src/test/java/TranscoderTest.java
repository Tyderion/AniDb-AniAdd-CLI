import config.blocks.MoveConfig;
import config.blocks.TranscodeConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import processing.FileHandler;
import transcode.MediaProber;
import transcode.Transcoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises the real ffmpeg and ffprobe against a generated clip, so the shipped default arguments
 * are known to be a valid command line rather than a plausible-looking string.
 */
public class TranscoderTest {

    private static boolean ffmpegAvailable;

    @BeforeAll
    public static void checkFfmpeg() {
        ffmpegAvailable = canRun("ffmpeg") && canRun("ffprobe");
    }

    private static boolean canRun(String binary) {
        try {
            return new ProcessBuilder(binary, "-version").start().waitFor(1, TimeUnit.MINUTES);
        } catch (Exception e) {
            return false;
        }
    }

    private Path generateSource(Path directory) throws Exception {
        Path source = directory.resolve("source.mkv");
        List<String> command = List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=24",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
                "-c:v", "libx264", "-profile:v", "high10", "-pix_fmt", "yuv420p10le",
                "-c:a", "aac", source.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat("generating the test clip failed: " + output, process.waitFor(), is(0));
        return source;
    }

    private TranscodeConfig config(Path originalsFolder) {
        return TranscodeConfig.builder()
                .enabled(true)
                .videoCodecs(List.of("h264"))
                .videoArgs("-c:v libx265 -crf 30 -preset ultrafast -pix_fmt yuv420p10le -x265-params log-level=none")
                .nice(0)
                .original(MoveConfig.HandlingConfig.builder()
                        .mode(MoveConfig.HandlingConfig.Mode.MOVE)
                        .folder(originalsFolder)
                        .build())
                .build();
    }

    @Test
    public void convertsH264ToHevcKeepsAudioAndMovesTheOriginalAside(@TempDir Path tempDir) throws Exception {
        assumeTrue(ffmpegAvailable, "ffmpeg and ffprobe are needed for this test");
        Path source = generateSource(tempDir);
        Path originals = Files.createDirectory(tempDir.resolve("originals"));
        MediaProber prober = new MediaProber("ffprobe");
        TranscodeConfig config = config(originals);

        try (Transcoder transcoder = new Transcoder(config, prober, new FileHandler())) {
            var sourceInfo = transcoder.matches(source);
            assertThat("h264 should match the configured input formats", sourceInfo.isPresent(), is(true));
            assertThat(sourceInfo.get().videoCodec(), is("h264"));

            AtomicReference<Optional<Transcoder.Result>> result = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            transcoder.transcode(source, sourceInfo.get(), transcodeResult -> {
                result.set(transcodeResult);
                done.countDown();
            });
            assertThat("transcode did not finish in time", done.await(5, TimeUnit.MINUTES), is(true));
            assertThat("transcode failed", result.get().isPresent(), is(true));

            Path converted = result.get().get().file();
            assertThat("the converted file takes the source's place", converted, is(source));
            assertThat(Files.exists(converted), is(true));
            assertThat("the original is kept aside", Files.exists(originals.resolve("source.mkv")), is(true));

            var convertedInfo = prober.probe(converted).orElseThrow();
            assertThat(convertedInfo.videoCodec(), is("hevc"));
            assertThat("audio is copied, not re-encoded", convertedInfo.audioCodecs(), is(List.of("aac")));
            assertThat(convertedInfo.durationSeconds(), is(closeTo(sourceInfo.get().durationSeconds(), 1.0)));
        }
    }

    @Test
    public void leavesFilesAloneWhenTheirCodecIsNotConfigured(@TempDir Path tempDir) throws Exception {
        assumeTrue(ffmpegAvailable, "ffmpeg and ffprobe are needed for this test");
        Path source = generateSource(tempDir);
        TranscodeConfig config = TranscodeConfig.builder()
                .enabled(true)
                .videoCodecs(List.of("hevc"))
                .build();

        try (Transcoder transcoder = new Transcoder(config, new MediaProber("ffprobe"), new FileHandler())) {
            assertThat(transcoder.matches(source).isPresent(), is(false));
        }
    }

    @Test
    public void doesNothingWhenDisabled(@TempDir Path tempDir) throws Exception {
        assumeTrue(ffmpegAvailable, "ffmpeg and ffprobe are needed for this test");
        Path source = generateSource(tempDir);
        TranscodeConfig config = TranscodeConfig.builder().enabled(false).build();

        try (Transcoder transcoder = new Transcoder(config, new MediaProber("ffprobe"), new FileHandler())) {
            assertThat(transcoder.matches(source).isPresent(), is(false));
        }
    }
}

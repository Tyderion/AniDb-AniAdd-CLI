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
import java.util.concurrent.TimeUnit;

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

    static boolean canRun(String binary) {
        try {
            return new ProcessBuilder(binary, "-version").start().waitFor(1, TimeUnit.MINUTES);
        } catch (Exception e) {
            return false;
        }
    }

    static Path generateSource(Path file) throws Exception {
        List<String> command = List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=24",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
                "-c:v", "libx264", "-profile:v", "high10", "-pix_fmt", "yuv420p10le",
                "-c:a", "aac", file.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat("generating the test clip failed: " + output, process.waitFor(), is(0));
        return file;
    }

    static final String FAST_VIDEO_ARGS = "-c:v libx265 -crf 30 -preset ultrafast -pix_fmt yuv420p10le -x265-params log-level=none";

    private TranscodeConfig.TranscodeConfigBuilder config(Path originalsFolder) {
        return TranscodeConfig.builder()
                .mode(TranscodeConfig.Mode.QUEUE)
                .videoArgs(FAST_VIDEO_ARGS)
                .nice(0)
                .original(MoveConfig.HandlingConfig.builder()
                        .mode(MoveConfig.HandlingConfig.Mode.MOVE)
                        .folder(originalsFolder)
                        .build());
    }

    @Test
    public void encodesH264ToHevcKeepingAudioAndLeavesTheSourceAlone(@TempDir Path tempDir) throws Exception {
        assumeTrue(ffmpegAvailable, "ffmpeg and ffprobe are needed for this test");
        Path source = generateSource(tempDir.resolve("source.mkv"));
        MediaProber prober = new MediaProber("ffprobe");
        Transcoder transcoder = new Transcoder(config(tempDir.resolve("originals")).build(), prober, new FileHandler());

        var sourceInfo = transcoder.matches(source);
        assertThat("h264 should match the configured input formats", sourceInfo.isPresent(), is(true));
        assertThat(sourceInfo.get().videoProfile(), is("High 10"));

        Path target = tempDir.resolve(".source.transcoding.mkv");
        var converted = transcoder.encode(source, sourceInfo.get(), target);

        assertThat("transcode failed", converted.isPresent(), is(true));
        assertThat("encode never touches the source", Files.exists(source), is(true));
        assertThat(converted.get().videoCodec(), is("hevc"));
        assertThat("audio is copied, not re-encoded", converted.get().audioCodecs(), is(List.of("aac")));
        assertThat(converted.get().durationSeconds(), is(closeTo(sourceInfo.get().durationSeconds(), 1.0)));
        assertThat("ffmpeg's log file is cleaned up", Files.exists(tempDir.resolve(".source.transcoding.mkv.log")), is(false));
    }

    @Test
    public void theShippedDefaultArgumentsAreAValidCommandLine(@TempDir Path tempDir) throws Exception {
        // The first version of the default carried "-x265-params profile=main10", which ffmpeg accepted
        // while x265 rejected it as an unknown option. Only running it catches that class of typo.
        assumeTrue(ffmpegAvailable, "ffmpeg and ffprobe are needed for this test");
        Path source = generateSource(tempDir.resolve("source.mkv"));
        MediaProber prober = new MediaProber("ffprobe");
        TranscodeConfig config = TranscodeConfig.builder().mode(TranscodeConfig.Mode.QUEUE).nice(0).build();
        Transcoder transcoder = new Transcoder(config, prober, new FileHandler());

        var sourceInfo = transcoder.matches(source);
        assertThat("h264 matches the shipped match default", sourceInfo.isPresent(), is(true));
        var converted = transcoder.encode(source, sourceInfo.get(), tempDir.resolve("out.mkv"));
        assertThat("the shipped default arguments failed to produce a valid encode", converted.isPresent(), is(true));
        assertThat(converted.get().videoCodec(), is("hevc"));
    }

    @Test
    public void matchesOnCodecProfileAndBitrate(@TempDir Path tempDir) throws Exception {
        assumeTrue(ffmpegAvailable, "ffmpeg and ffprobe are needed for this test");
        Path source = generateSource(tempDir.resolve("source.mkv"));
        MediaProber prober = new MediaProber("ffprobe");

        assertThat(matches(prober, source, TranscodeConfig.MatchConfig.builder().videoCodecs(List.of("hevc")).build()), is(false));
        assertThat(matches(prober, source, TranscodeConfig.MatchConfig.builder().profiles(List.of("high 10")).build()), is(true));
        assertThat(matches(prober, source, TranscodeConfig.MatchConfig.builder().profiles(List.of("High")).build()), is(false));
        assertThat(matches(prober, source, TranscodeConfig.MatchConfig.builder().minBitrateKbps(1_000_000).build()), is(false));
    }

    private boolean matches(MediaProber prober, Path source, TranscodeConfig.MatchConfig match) {
        TranscodeConfig config = TranscodeConfig.builder().match(match).build();
        return new Transcoder(config, prober, new FileHandler()).matches(source).isPresent();
    }

    @Test
    public void handlesTheOriginalPerConfig(@TempDir Path tempDir) throws Exception {
        Path originals = tempDir.resolve("originals");
        Path moved = Files.writeString(tempDir.resolve("moved.mkv"), "x");
        Path deleted = Files.writeString(tempDir.resolve("deleted.mkv"), "x");
        Path kept = Files.writeString(tempDir.resolve("kept.mkv"), "x");
        MediaProber prober = new MediaProber("ffprobe");

        assertThat(new Transcoder(config(originals).build(), prober, new FileHandler()).handleOriginal(moved), is(true));
        assertThat(Files.exists(originals.resolve("moved.mkv")), is(true));

        var delete = config(originals).original(MoveConfig.HandlingConfig.builder().mode(MoveConfig.HandlingConfig.Mode.DELETE).build()).build();
        assertThat(new Transcoder(delete, prober, new FileHandler()).handleOriginal(deleted), is(true));
        assertThat(Files.exists(deleted), is(false));

        var keep = config(originals).original(MoveConfig.HandlingConfig.builder().mode(MoveConfig.HandlingConfig.Mode.NONE).build()).build();
        assertThat(new Transcoder(keep, prober, new FileHandler()).handleOriginal(kept), is(true));
        assertThat(Files.exists(kept), is(true));
    }
}

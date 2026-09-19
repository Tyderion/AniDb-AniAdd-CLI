package config.blocks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Optional;

/**
 * Transcoding is a separate stage. The scan/watch pipeline only queues jobs (mode queue) for files it
 * identified and moved; a dedicated `anidb transcode` process works through that queue. The queue lives
 * in the sqlite cache, so a restart of either process neither loses a pending job nor redoes a finished one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class TranscodeConfig {
    /**
     * Chosen by measurement, on a grainy 1080p Blu-ray anime opening scored against its source:
     * CRF 18 to 22 came out near-lossless and larger than the original (VMAF 99.3+, 103-151% of the
     * source size), CRF 24 lands at 83% of the source at VMAF 98.97, and CRF 30 drops to 41% at 96.26.
     * Gabriel could not distinguish any of them on the target hardware, so 24 is the middle ground
     * rather than the smallest file. CAMBI showed no banding differences anywhere in that range.
     * <p>
     * The x265 params are the ones anime encoders reach for; by VMAF they are a wash against defaults
     * (about 0.1 better at equal bitrate), and they are kept because CRF 24 with them is exactly what
     * was reviewed on the TV. 10-bit is explicit to match the target format, and note that x265 derives
     * its profile from the pixel format: profile= inside -x265-params is rejected as unknown.
     * <p>
     * Cost: preset slow ran at roughly 0.33x realtime on a Ryzen 7 5800X3D, so about an hour per
     * 24-minute episode there, and slower on a NAS.
     */
    private static final String DEFAULT_VIDEO_ARGS = "-c:v libx265 -crf 24 -preset slow -pix_fmt yuv420p10le -x265-params no-sao=1:aq-mode=3:psy-rd=1.5:deblock=-1,-1:bframes=8:ref=4:rc-lookahead=60";
    private static final String DEFAULT_STREAM_ARGS = "-map 0 -c:a copy -c:s copy -c:t copy -map_metadata 0 -map_chapters 0";

    public enum Mode {
        /** Nothing is queued. */
        OFF,
        /** The scan/watch pipeline queues matching files once they are identified and moved. */
        QUEUE
    }

    public enum Existing {
        /** A release that was already converted is never queued again. */
        SKIP,
        /** A finished or failed job for the release is reset and runs again. */
        RETRANSCODE
    }

    public enum Nfo {
        /** The episode/movie NFO is only renamed along with the video. */
        KEEP,
        /** The renamed NFO also gets the new video codec in its stream details. */
        PATCH
    }

    @Builder.Default
    private Mode mode = Mode.OFF;

    @Builder.Default
    private MatchConfig match = MatchConfig.builder().build();

    @Builder.Default
    private Existing existing = Existing.SKIP;

    @Builder.Default
    private Nfo nfo = Nfo.PATCH;

    /**
     * How often a job may fail before it is marked failed for good. A failed verification is usually
     * deterministic, so retrying many times only burns hours of encoding.
     */
    @Builder.Default
    private Integer maxAttempts = 2;

    /**
     * How long the transcoder waits before looking at the queue again when it is empty.
     */
    @Builder.Default
    private Integer pollSeconds = 60;

    @Builder.Default
    private String ffmpegPath = "ffmpeg";
    @Builder.Default
    private String ffprobePath = "ffprobe";

    @Builder.Default
    private String videoArgs = DEFAULT_VIDEO_ARGS;

    /**
     * Everything that is not video. Audio, subtitles, attachments and chapters are copied, so the
     * only lossy step is the video re-encode.
     */
    @Builder.Default
    private String streamArgs = DEFAULT_STREAM_ARGS;

    /**
     * nice keeps encodes off the NAS's back. In docker, a cpus limit on the transcoder container does more.
     */
    @Builder.Default
    private Integer nice = 10;

    @Builder.Default
    private Integer timeoutMinutes = 480;

    /**
     * Verification thresholds. A converted file replaces the original only when ffmpeg exits 0, the
     * duration still matches, the output is not suspiciously small, and no stream went missing.
     */
    @Builder.Default
    private Double durationToleranceSeconds = 1.0;
    @Builder.Default
    private Double minSizeRatio = 0.05;

    /**
     * What happens to the source file once the converted one passed verification. MOVE needs a
     * folder; anything else ignores it.
     */
    @Builder.Default
    private MoveConfig.HandlingConfig original = MoveConfig.HandlingConfig.builder()
            .mode(MoveConfig.HandlingConfig.Mode.MOVE)
            .build();

    /**
     * Which files get converted. All given criteria must hold; an empty list or a missing value does not restrict.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class MatchConfig {
        /** Video codec as ffprobe names it, e.g. h264. */
        @Builder.Default
        private List<String> videoCodecs = List.of("h264");
        /** Video profile as ffprobe names it, e.g. "High 10". Empty matches any profile. */
        @Builder.Default
        private List<String> profiles = List.of();
        /** Only files with at least this overall bitrate. Missing matches any bitrate. */
        private Integer minBitrateKbps;
    }

    public void removeDefaults() {
        if (videoArgs != null && videoArgs.equals(DEFAULT_VIDEO_ARGS)) {
            videoArgs = null;
        }
        if (streamArgs != null && streamArgs.equals(DEFAULT_STREAM_ARGS)) {
            streamArgs = null;
        }
    }

    /**
     * @return the reason the pipeline cannot queue with this config, or empty when it is usable or off.
     */
    public Optional<String> validationError() {
        return mode == Mode.OFF ? Optional.empty() : runnerValidationError();
    }

    /**
     * The transcoder process uses the block regardless of mode, since running it is the opt-in.
     *
     * @return the reason the transcoder cannot work with this config, or empty when it is usable.
     */
    public Optional<String> runnerValidationError() {
        if (match == null || match.videoCodecs() == null || match.videoCodecs().isEmpty()) {
            return Optional.of("transcode.match.videoCodecs is empty, so nothing would ever match");
        }
        if (original.mode() == MoveConfig.HandlingConfig.Mode.MOVE && original.folder() == null) {
            return Optional.of("transcode.original.mode is move but transcode.original.folder is not set");
        }
        if (maxAttempts == null || maxAttempts < 1) {
            return Optional.of("transcode.maxAttempts must be at least 1");
        }
        if (pollSeconds == null || pollSeconds < 1) {
            return Optional.of("transcode.pollSeconds must be at least 1");
        }
        return Optional.empty();
    }
}

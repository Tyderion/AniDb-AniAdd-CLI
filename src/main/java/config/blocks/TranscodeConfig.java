package config.blocks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class TranscodeConfig {
    /**
     * PROVISIONAL default, not a considered choice: crf and preset were never benchmarked against the
     * library. Tune videoArgs before running this over anything you care about. 10-bit is explicit
     * because that is what the target format uses; note that x265's profile is derived from the pixel
     * format, and passing profile= through -x265-params is rejected as an unknown option.
     */
    private static final String DEFAULT_VIDEO_ARGS = "-c:v libx265 -crf 23 -preset medium -pix_fmt yuv420p10le";
    private static final String DEFAULT_STREAM_ARGS = "-map 0 -c:a copy -c:s copy -c:t copy -map_metadata 0 -map_chapters 0";

    @Builder.Default
    private boolean enabled = false;

    /**
     * A file is transcoded when its video codec (as ffprobe names it) is in this list.
     */
    @Builder.Default
    private List<String> videoCodecs = List.of("h264");

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
     * Encodes run one at a time on their own thread. nice keeps them off the NAS's back.
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

    public void removeDefaults() {
        if (videoArgs != null && videoArgs.equals(DEFAULT_VIDEO_ARGS)) {
            videoArgs = null;
        }
        if (streamArgs != null && streamArgs.equals(DEFAULT_STREAM_ARGS)) {
            streamArgs = null;
        }
    }

    /**
     * @return the reason this config cannot be used, or empty when it is usable.
     */
    public java.util.Optional<String> validationError() {
        if (!enabled) {
            return java.util.Optional.empty();
        }
        if (videoCodecs == null || videoCodecs.isEmpty()) {
            return java.util.Optional.of("transcode.videoCodecs is empty, so nothing would ever match");
        }
        if (original.mode() == MoveConfig.HandlingConfig.Mode.MOVE && original.folder() == null) {
            return java.util.Optional.of("transcode.original.mode is move but transcode.original.folder is not set");
        }
        return java.util.Optional.empty();
    }
}

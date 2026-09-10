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

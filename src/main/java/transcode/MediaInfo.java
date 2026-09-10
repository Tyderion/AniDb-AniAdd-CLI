package transcode;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * The subset of ffprobe output the transcoder cares about: what to match on, and what to verify
 * against after an encode.
 */
@Data
@Builder
@Accessors(fluent = true)
public class MediaInfo {
    private final String videoCodec;
    private final List<String> audioCodecs;
    private final double durationSeconds;
    private final long sizeInBytes;
    private final int videoStreams;
    private final int audioStreams;
    private final int subtitleStreams;
    private final int attachmentStreams;
}

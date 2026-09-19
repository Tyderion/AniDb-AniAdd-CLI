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
    /** As ffprobe names it, e.g. "High 10". Null when the stream reports none. */
    private final String videoProfile;
    /** Overall bitrate of the file, 0 when ffprobe reports none. */
    private final long bitRateKbps;
    private final List<String> audioCodecs;
    private final double durationSeconds;
    private final long sizeInBytes;
    private final int videoStreams;
    private final int audioStreams;
    private final int subtitleStreams;
    private final int attachmentStreams;
}

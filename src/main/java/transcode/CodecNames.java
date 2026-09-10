package transcode;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Map;

/**
 * ffprobe and AniDB name codecs differently, and the tag system speaks AniDB's dialect: a tagging
 * script matching on "H264/AVC" must keep working after a local re-encode, and must produce the
 * same "[HEVC]" the library already uses for AniDB-known HEVC releases. Everything unknown falls
 * back to the uppercased ffprobe name with a warning, so a surprise codec is visible rather than
 * silently written as something wrong.
 */
@Slf4j
public final class CodecNames {
    private static final Map<String, String> FFPROBE_TO_ANIDB = Map.ofEntries(
            Map.entry("h264", "H264/AVC"),
            Map.entry("hevc", "HEVC"),
            Map.entry("av1", "AV1"),
            Map.entry("vp9", "VP9"),
            Map.entry("vp8", "VP8"),
            Map.entry("mpeg4", "MPEG-4"),
            Map.entry("mpeg2video", "MPEG-2"),
            Map.entry("mpeg1video", "MPEG-1"),
            Map.entry("vc1", "VC-1"),
            Map.entry("wmv3", "WMV9"),
            Map.entry("theora", "Theora"));

    private CodecNames() {
    }

    public static String toAniDbVideoCodec(String ffprobeCodec) {
        if (ffprobeCodec == null || ffprobeCodec.isBlank()) {
            return null;
        }
        val mapped = FFPROBE_TO_ANIDB.get(ffprobeCodec.toLowerCase());
        if (mapped == null) {
            log.warn(STR."No AniDB name known for video codec '\{ffprobeCodec}', using it uppercased. Add it to CodecNames if your tag system needs a specific spelling.");
            return ffprobeCodec.toUpperCase();
        }
        return mapped;
    }
}

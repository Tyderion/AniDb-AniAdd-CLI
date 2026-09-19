package transcode;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Updates the video codec in an existing Kodi NFO after a transcode. The NFO was written from AniDB's
 * data about the original release, so its stream details still name the old codec.
 * <p>
 * Only the text of that one element changes. Rewriting the whole document would need the TVDB/TMDB data
 * again and would drop what other runs added, like the watched state from MyList.
 */
@Slf4j
public final class NfoPatcher {
    private static final Pattern VIDEO_CODEC = Pattern.compile(
            "(<streamdetails>\\s*<video>(?:(?!</video>).)*?<codec>)(.*?)(</codec>)", Pattern.DOTALL);

    private NfoPatcher() {
    }

    /**
     * @return true when the NFO exists and now names the given codec
     */
    public static boolean patchVideoCodec(Path nfo, String codec) {
        if (codec == null || !Files.isRegularFile(nfo)) {
            return false;
        }
        try {
            val content = Files.readString(nfo, StandardCharsets.UTF_8);
            val matcher = VIDEO_CODEC.matcher(content);
            if (!matcher.find()) {
                log.warn(STR."No video codec in the stream details of \{nfo}, leaving it as it is");
                return false;
            }
            val patched = content.substring(0, matcher.start()) + matcher.group(1) + codec + matcher.group(3) + content.substring(matcher.end());
            if (!patched.equals(content)) {
                Files.writeString(nfo, patched, StandardCharsets.UTF_8);
                log.info(STR."Set the video codec in \{nfo.getFileName()} to \{codec}");
            }
            return true;
        } catch (Exception e) {
            log.error(STR."Could not patch \{nfo}: \{e.getMessage()}");
            return false;
        }
    }
}

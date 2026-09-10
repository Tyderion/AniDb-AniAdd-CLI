import org.junit.jupiter.api.Test;
import transcode.CodecNames;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class CodecNamesTest {

    @Test
    public void mapsFfprobeNamesToTheVocabularyTheTagSystemExpects() {
        // A tagging script matching "H264/AVC" and printing "[HEVC]" has to keep working for files we
        // encoded ourselves, so ffprobe's spelling is translated into AniDB's.
        assertThat(CodecNames.toAniDbVideoCodec("h264"), is("H264/AVC"));
        assertThat(CodecNames.toAniDbVideoCodec("hevc"), is("HEVC"));
        assertThat(CodecNames.toAniDbVideoCodec("HEVC"), is("HEVC"));
    }

    @Test
    public void fallsBackToTheUppercasedNameForUnknownCodecs() {
        assertThat(CodecNames.toAniDbVideoCodec("someNewCodec"), is("SOMENEWCODEC"));
    }

    @Test
    public void handlesMissingCodec() {
        assertThat(CodecNames.toAniDbVideoCodec(null), is(nullValue()));
        assertThat(CodecNames.toAniDbVideoCodec("  "), is(nullValue()));
    }
}

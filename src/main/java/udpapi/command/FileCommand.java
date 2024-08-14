package udpapi.command;

import aniAdd.misc.Misc;
import lombok.experimental.SuperBuilder;
import lombok.val;
import processing.tagsystem.TagSystemTags;
import udpapi.QueryId;
import udpapi.reply.Reply;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
public class FileCommand extends Command {
    private static final String ACTION = "FILE";

    private static final BitSet FILE_MASK = createFileMask();
    private static final BitSet ANIME_MASK = createAnimeMask();

    public static FileCommand Create(int fileId, long length, String ed2k) {
        return FileCommand.builder()
                .action(ACTION)
                .identifier(ACTION.toLowerCase())
                .queryId(QueryId.Next())
                .needsLogin(true)
                .tag(fileId)
                .parameter("fmask", Misc.toMask(FILE_MASK, 40))
                .parameter("amask", Misc.toMask(ANIME_MASK, 32))
                .parameter("size", String.valueOf(length))
                .parameter("ed2k", ed2k)
                .build();
    }

    public static void AddReplyToDict(Map<TagSystemTags, String> map, Reply reply, Boolean forceWatchedState) {
        ArrayDeque<String> df = new ArrayDeque<>(reply.getResponseData());
        map.put(TagSystemTags.FileId, df.poll());
        map.put(TagSystemTags.AnimeId, df.poll());
        map.put(TagSystemTags.EpisodeId, df.poll());
        map.put(TagSystemTags.GroupId, df.poll());
        map.put(TagSystemTags.MyListId, df.poll());
        map.put(TagSystemTags.OtherEpisodes, df.poll());
        map.put(TagSystemTags.Deprecated, df.poll());

        val state = Integer.parseInt(Objects.requireNonNull(df.poll()));
        map.put(TagSystemTags.CrcOK, (state & 1 << 0) != 0 ? "1" : "");
        map.put(TagSystemTags.CrcError, (state & 1 << 1) != 0 ? "1" : "");
        map.put(TagSystemTags.Censored, (state & 1 << 7) != 0 ? "1" : "");
        map.put(TagSystemTags.Uncensored, (state & 1 << 6) != 0 ? "1" : "");
        map.put(TagSystemTags.Version, GetFileVersion(state).toString());


        map.put(TagSystemTags.FileCrc, df.poll());
        map.put(TagSystemTags.FileColorDepth, df.poll());
        map.put(TagSystemTags.Quality, df.poll());
        map.put(TagSystemTags.Source, df.poll());
        map.put(TagSystemTags.FileAudioCodec, df.poll());
        map.put(TagSystemTags.FileVideoCodec, df.poll());
        map.put(TagSystemTags.FileVideoResolution, df.poll());
        map.put(TagSystemTags.FileAudioLanguage, df.poll());
        map.put(TagSystemTags.FileSubtitleLanguage, df.poll());
        map.put(TagSystemTags.FileDuration, df.poll());
        map.put(TagSystemTags.EpisodeAirDate, df.poll());
        map.put(TagSystemTags.FileAnidbFilename, df.poll());

        val dbIsWatched = Objects.requireNonNull(df.poll());
        val watched = (forceWatchedState != null && forceWatchedState || forceWatchedState == null && dbIsWatched.equals("1")) ? "1" : "";
        map.put(TagSystemTags.Watched, watched);

        map.put(TagSystemTags.EpisodeCount, df.poll());
        map.put(TagSystemTags.EpisodeHiNumber, df.poll());

        String[] year = Objects.requireNonNull(df.poll()).split("-");
        map.put(TagSystemTags.SeriesYearBegin, year[0]);
        if (year.length == 2) {
            map.put(TagSystemTags.SeriesYearEnd, year[1]);
        }
        map.put(TagSystemTags.Type, df.poll());
        map.put(TagSystemTags.SeriesCategoryList, df.poll());
        map.put(TagSystemTags.SeriesNameRomaji, df.poll());
        map.put(TagSystemTags.SeriesNameKanji, df.poll());
        map.put(TagSystemTags.SeriesNameEnglish, df.poll());
        map.put(TagSystemTags.SeriesNameOther, df.poll());
        map.put(TagSystemTags.EpisodeNumber, df.poll());
        map.put(TagSystemTags.EpisodeNameEnglish, df.poll());
        map.put(TagSystemTags.EpisodeNameRomaji, df.poll());
        map.put(TagSystemTags.EpisodeNameKanji, df.poll());
        map.put(TagSystemTags.GroupNameLong, df.poll());
        map.put(TagSystemTags.GroupNameShort, df.poll());
    }

    private static BitSet createAnimeMask() {
        val binCode = new BitSet(32);
        binCode.set(1); //'category list
        binCode.set(4); //'type
        binCode.set(5); //'year
        binCode.set(6); //'highest EpCount
        binCode.set(7); //'epCount

        binCode.set(12); //'other name
        binCode.set(13); //'english name
        binCode.set(14); //'kanji name
        binCode.set(15); //'romaji name

        binCode.set(20); //'ep kanji
        binCode.set(21); //'ep romaji
        binCode.set(22); //'ep name
        binCode.set(23); //'epno

        binCode.set(30); //'group short name
        binCode.set(31); //'group name
        return binCode;
    }

    private static BitSet createFileMask() {
        val binCode = new BitSet(64);
        binCode.set(0); //'state
        binCode.set(1); //'Depr
        binCode.set(2); //'other eps //new
        binCode.set(3); //'lid
        binCode.set(4); //gid
        binCode.set(5); //eid
        binCode.set(6); //aid

        binCode.set(9); //'bit depth //new
        binCode.set(11); //'crc

        binCode.set(17); //'video res
        binCode.set(19); //'VideoCodec
        binCode.set(21); //'AudioCodec
        binCode.set(22); //'Source
        binCode.set(23); //'Quality

        binCode.set(24); //'anidb filename scheme
        binCode.set(27); //'air date //new
        binCode.set(29); //'length in seconds //new
        binCode.set(30); //'sub lang list
        binCode.set(31); //'dub lang list

        binCode.set(37); //'watched state

        return binCode;
    }

    private static Integer GetFileVersion(int state) {
        int verFlag = (state & (4 + 8 + 16 + 32)) >> 2;
        int version = 1;

        while (verFlag != 0) {
            version++;
            verFlag = verFlag >> 1;
        }

        return version;
    }
}

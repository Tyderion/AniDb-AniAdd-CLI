package udpapi2.command;

import aniAdd.misc.Misc;
import lombok.experimental.SuperBuilder;
import lombok.val;
import udpapi2.QueryId;

import java.util.BitSet;

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
                .tag(String.valueOf(fileId))
                .parameter("fmask", Misc.toMask(FILE_MASK, 40))
                .parameter("amask", Misc.toMask(ANIME_MASK, 32))
                .parameter("size", String.valueOf(length))
                .parameter("ed2k", ed2k)
                .build();
    }

    private static BitSet createAnimeMask() {
        val binCode = new BitSet(32);
        binCode.set(1); //'category list
        binCode.set(4); //'type
        binCode.set(5); //'year
        binCode.set(6); //'highest EpCount
        binCode.set(7); //'epCount

        binCode.set(10); //'synonym
        binCode.set(11); //'short name
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
}

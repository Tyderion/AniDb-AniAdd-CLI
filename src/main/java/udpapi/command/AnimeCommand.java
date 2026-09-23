package udpapi.command;

import lombok.experimental.SuperBuilder;
import lombok.val;
import udpapi.QueryId;

import java.util.BitSet;

@SuperBuilder(toBuilder = true)
public class AnimeCommand extends Command {
    private static final String ACTION = "ANIME ";

    public static AnimeCommand Create(int animeId) {
        val command = AnimeCommand.builder()
                .action(ACTION)
                .identifier("a")
                .queryId(QueryId.Next())
                .needsLogin(true)
                .parameter("aid", String.valueOf(animeId))
                .tag(animeId);

        return command.build();
    }

    private static BitSet createAnimeMask() {
        val binCode = new BitSet(56);
        binCode.set(6); //'dateflags
        binCode.set(7); //'animeId

//        binCode.set(12); //'other name
//        binCode.set(13); //'english name
//        binCode.set(14); //'kanji name
//        binCode.set(15); //'romaji name

        binCode.set(19); //'end date
        binCode.set(20); //'air date
        binCode.set(22); //'ep name
        binCode.set(23); //'epno

        binCode.set(30); //'group short name
        binCode.set(31); //'group name
        return binCode;
    }
}

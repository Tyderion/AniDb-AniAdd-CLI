package processing;

import aniAdd.IAniAdd;
import aniAdd.Modules.BaseModule;
import aniAdd.Modules.IModule;
import aniAdd.misc.Misc;
import aniAdd.misc.MultiKeyDict;
import udpApi.Cmd;
import udpApi.Mod_UdpApi;
import udpApi.Query;

import java.util.ArrayDeque;
import java.util.BitSet;

public class Mod_AnimeProcessing extends BaseModule {
    private IAniAdd aniAdd;
    private Mod_UdpApi api;
    private MultiKeyDict<String, Object, AnimeInfo> anime;


    private void requestDBAnimeInfo(int animeId) {
        Cmd cmd = new Cmd("ANIME", "aid", Integer.toString(animeId), true);

        BitSet binCode = new BitSet(64);
//        binCode.set(0); //'retired
//        binCode.set(1); //'retired
        binCode.set(2); //'str related aid type
        binCode.set(3); //'str related aid list
        binCode.set(4); //str type
        binCode.set(5); //str year
        binCode.set(6); //int dateflags
        binCode.set(7); // int aid

//        binCode.set(8); // retired
//        binCode.set(9); //retired
        binCode.set(10); // str synonym list
        binCode.set(11); //str short name list
        binCode.set(12); // 	str other name
        binCode.set(13); // 	str english name
        binCode.set(14); // 	str kanji name
        binCode.set(15); // 	str romaji name

//        binCode.set(16); // retired
        binCode.set(17); // str picname
        binCode.set(18); //str url
        binCode.set(19); //  	int end date
        binCode.set(20); //  	int air date
        binCode.set(21); //  	int4 special ep count
        binCode.set(22); // int4 highest episode number
        binCode.set(23); // int4 episodes

        binCode.set(24); //'bool is 18+ restricted
        binCode.set(25); //'str award list
        binCode.set(26); //'int review count
        binCode.set(27); //'int4 average review rating
        binCode.set(29); //'int temp vote count
        binCode.set(29); //'int4 temp rating
        binCode.set(30); //'int vote count
        binCode.set(31); //'int4 rating

        binCode.set(32); //'int date record updated
        binCode.set(33); //' 	int tag weight list
        binCode.set(34); //'int tag id list
        binCode.set(35); //' 	str tag name list
//        binCode.set(36); //'str AnimeNfo id
//        binCode.set(37); //'int allcinema id
//        binCode.set(38); //'int ANN id
//        binCode.set(39); //'retired

//        binCode.set(40); //'unused
//        binCode.set(41); //'unused
//        binCode.set(42); //'unused
//        binCode.set(43); //'unused
//        binCode.set(44); //'retired
//        binCode.set(45); //'retired
//        binCode.set(46); //'retired
        binCode.set(47); //'int character id list

        cmd.setArgs("amask", Misc.toMask(binCode, 32));

        api.queryCmd(cmd);
        //System.out.println("Sending File Cmd");
    }

    private void aniDBAnimeReply(int queryId) {
        //System.out.println("Got Fileinfo reply");

        Query query = api.Queries().get(queryId);
        int replyId = query.getReply().ReplyId();

        int animeId = Integer.parseInt(query.getReply().Tag());
        if (!anime.contains("Id", animeId)) {
            return; //File not found (Todo: throw error)
        }
        AnimeInfo animeInfo = anime.get("Id", animeId);
        animeInfo.ActionsTodo().remove(AnimeInfo.eAction.AnimeCmd);

        if (replyId == 330 || replyId == 505 || replyId == 322) {
            animeInfo.ActionsError().add(AnimeInfo.eAction.AnimeCmd);
            Log(CommunicationEvent.EventType.Information, Mod_AnimeProcessing.eComType.FileEvent, replyId == 320 ? Mod_AnimeProcessing.eComSubType.FileCmd_NotFound : Mod_AnimeProcessing.eComSubType.FileCmd_Error, animeInfo.Id());
        } else {
            animeInfo.ActionsDone().add(AnimeInfo.eAction.AnimeCmd);
            ArrayDeque<String> df = new ArrayDeque<String>(query.getReply().DataField());
            animeInfo.Data().put("DB_AID", df.poll());
            animeInfo.Data().put("DB_Dateflags", df.poll());
            animeInfo.Data().put("DB_Year", df.poll());
            animeInfo.Data().put("DB_Type", df.poll());
            animeInfo.Data().put("DB_RelatedAid", df.poll());
            animeInfo.Data().put("DB_RelatedAidType", df.poll());

            animeInfo.Data().put("DB_SN_Romaji", df.poll());
            animeInfo.Data().put("DB_SN_Kanji", df.poll());
            animeInfo.Data().put("DB_SN_English", df.poll());
            animeInfo.Data().put("DB_SN_Other", df.poll());
            animeInfo.Data().put("DB_SN_Short", df.poll());
            animeInfo.Data().put("DB_SN_Synonym", df.poll());

            animeInfo.Data().put("DB_EpCount", df.poll());
            animeInfo.Data().put("DB_EpHiCount", df.poll());
            animeInfo.Data().put("DB_SpecialEpCount", df.poll());
            animeInfo.Data().put("DB_AirDate", df.poll());
            animeInfo.Data().put("DB_EndDate", df.poll());
            animeInfo.Data().put("DB_Url", df.poll());
            animeInfo.Data().put("DB_PicName", df.poll());

            animeInfo.Data().put("DB_Rating", df.poll());
            animeInfo.Data().put("DB_VoteCount", df.poll());
            animeInfo.Data().put("DB_TempRating", df.poll());
            animeInfo.Data().put("DB_TempVoteCount", df.poll());
            animeInfo.Data().put("DB_AvgReviewRating", df.poll());
            animeInfo.Data().put("DB_ReviewCount", df.poll());
            animeInfo.Data().put("DB_AwardList", df.poll());
            animeInfo.Data().put("DB_18Plus", df.poll());

            animeInfo.Data().put("DB_TagNameList", df.poll());
            animeInfo.Data().put("DB_TagIdList", df.poll());
            animeInfo.Data().put("DB_TagWeightList", df.poll());
            animeInfo.Data().put("DB_DateRecordUpdated", df.poll());

            animeInfo.Data().put("DB_CharIdList", df.poll());

            Log(CommunicationEvent.EventType.Information, Mod_AnimeProcessing.eComType.FileEvent, Mod_AnimeProcessing.eComSubType.FileCmd_GotInfo, animeInfo.Id());
        }

        if (!animeInfo.IsFinal() && !(animeInfo.ActionsTodo().contains(AnimeInfo.eAction.AnimeCmd))) {
            finalProcessing(animeInfo);
        }
    }

    private void finalProcessing(AnimeInfo animeInfo) {
        // TODO
    }


    // <editor-fold defaultstate="collapsed" desc="IModule">
    protected String modName = "AnimeProcessing";
    protected IModule.eModState modState = IModule.eModState.New;

    public IModule.eModState ModState() {
        return modState;
    }

    public String ModuleName() {
        return modName;
    }

    public void Initialize(IAniAdd aniAdd) {
        modState = IModule.eModState.Initializing;

        this.aniAdd = aniAdd;
        api = aniAdd.GetModule(Mod_UdpApi.class);

        api.registerEvent(this::aniDBAnimeReply, "anime");

        modState = IModule.eModState.Initialized;
    }

    public void Terminate() {
        modState = IModule.eModState.Terminating;


        modState = IModule.eModState.Terminated;
    }

    // </editor-fold>

    public enum eProcess {

        Start, Pause, Resume, Stop
    }

    public enum eComType {

        FileSettings,
        FileCountChanged,
        FileEvent,
        Status
    }

    public enum eComSubType {

        Processing,
        NoWriteAccess,
        GotFromHistory,
        ParsingDone,
        ParsingError,
        GetDBInfo,
        FileCmd_NotFound,
        FileCmd_GotInfo,
        FileCmd_Error,
        MLCmd_FileAdded,
        MLCmd_AlreadyAdded,
        MLCmd_FileRemoved,
        MLCmd_NotFound,
        MLCmd_Error,
        VoteCmd_EpVoted,
        VoteCmd_EpVoteRevoked,
        VoteCmd_Error,
        RenamingFailed,
        FileRenamed,
        RenamingNotNeeded,
        RelFilesRenamed,
        RelFilesRenamingFailed,
        DeletedEmptyFolder,
        DeletetingEmptyFolderFailed,
        Done
    }

}

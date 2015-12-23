package aniAdd.config;


/**
 * Created by Archie on 23.12.2015.
 */
public class AniConfiguration {
    private boolean mAddToMylist = false;
    private boolean mAdvancedMode = false;
    private String mTestString = "This is a test";
    private boolean mMoveTypeUseFolder;
    private String mTagSystemCode = "BaseTVShowPath:=\"H:\\Anime\\TV Shows\\\"                       #Set to your root folder for anime TV shows\n" +
            "BaseMoviePath:=\"H:\\Anime\\Movies\\\"                          #Set to your root folder for anime movies\n" +
            "ShowTitle:=[%ATr%, %ATe%, %ATk%]\n" +
            "EpisodeTitle:=[%ETe%, %ETr%, %ETk%]\n" +
            "ShowTitle:=$repl(%ShowTitle%, \"\\?|\\s*\\.+$\", \"_\")\n" +
            "ShowTitle:=$repl(%ShowTitle%, '[\\\\\":/*|<>?]', \" \")\n" +
            "EpisodeTitle:=$repl(%EpisodeTitle%, '[\\\\\":/*|<>?]', \" \")\n" +
            "ShowTitle:=$repl(%ShowTitle%, \"\\s+\", \" \")\n" +
            "EpisodeTitle:=$repl(%EpisodeTitle%, \"\\s+\", \" \")\n" +
            "ShowTitle:=$repl(%ShowTitle%, \"^\\s|\\s$\", \"\")\n" +
            "EpisodeTitle:=$repl(%EpisodeTitle%, \"^\\s|\\s$\", \"\")\n" +
            "ShowTitle:=$repl(%ShowTitle%, \"`\", \"'\")\n" +
            "EpisodeTitle:=$repl(%EpisodeTitle%, \"`\", \"'\")\n" +
            "Trunc(str, len):=$repl(%str%, \".{\" $len($repl(%str%, \"(.?){\" %len% \"}$\", \"\")) \"}$\", \"\")\n" +
            "TruncEllipse(str, len):={$len(%str%) = $len($Trunc(%str%, %len%)) ? %str% : $Trunc(%str%, %len%) \"…\" }\n" +
            "EpisodeTitle:=$TruncEllipse(%EpisodeTitle%, \"64\")\n" +
            "Regular:=\"\"\n" +
            "Special:=\"S\"\n" +
            "Separator:=\" - \"\n" +
            "SpecialEp:=$repl(%EpNo%, \"[1234567890]\", \"\")\n" +
            "EpNo:=$repl(%EpNo%, \"[SCTPO]\", \"\")\n" +
            "Ver:={%Ver% = \"1\" ? \"\" : \"v\" %Ver% }\n" +
            "Pad:={%SpecialEp% ? \"2\" : $max($len(%EpHiNo%), $len(%EpCount%)) }\n" +
            "Pad:={$match(%EpCount%, \"0\") ? $max(\"2\", %Pad%) : %Pad% }\n" +
            "EpNoPad:=$pad(%EpNo%, %Pad%, \"0\")\n" +
            "EpNoPad:={%SpecialEp%       ? %EpNoPad%  :  %Regular%   %EpNoPad% }\n" +
            "EpNoPad:={%SpecialEp% = \"S\" ? %Special%     %EpNoPad% : %EpNoPad% }\n" +
            "EpNoPad:={%SpecialEp% = \"C\" ? %Special% \"1\" %EpNoPad% : %EpNoPad% }\n" +
            "EpNoPad:={%SpecialEp% = \"T\" ? %Special% \"2\" %EpNoPad% : %EpNoPad% }\n" +
            "EpNoPad:={%SpecialEp% = \"P\" ? %Special% \"3\" %EpNoPad% : %EpNoPad% }\n" +
            "EpNoPad:={%SpecialEp% = \"O\" ? %Special% \"4\" %EpNoPad% : %EpNoPad% }\n" +
            "EpNoFull:=%Separator% %EpNoPad% %Ver% %Separator%\n" +
            "GT:=\"[\" [%GTs%, %GTl%] \"]\"\n" +
            "Src:=\"[\" $repl(%Source%, \"B-R|Blu-ray\", \"BluRay\") \"]\"\n" +
            "Cen:={%Cen% ? \"[Cen]\" : \"\" }\n" +
            "Res:=\"[\" %FVideoRes% \"]\"\n" +
            "VCodec:=\"[\" $repl(%FVCodec%,\"H264/AVC\",\"h264\") \"]\"\n" +
            "ACodec:=\"[\" %FACodec% \"]\"\n" +
            "CRC:=\"[\" $uc(%FCrc%) \"]\"\n" +
            "FileInfo:=\" \" %GT% %Src% %Cen% %Res% %VCodec% %CRC%\n" +
            "MovieTypes:=\"Movie|OVA\"\n" +
            "IsMovie:={$match(%Type%, %MovieTypes%) ? {%EpCount% = \"1\" ? {%SpecialEp% ? \"\" : \"1\" } : \"\" } : \"\" }\n" +
            "MovieFileName:=%ShowTitle% %FileInfo%\n" +
            "TVShowFileName:=%ShowTitle% %EpNoFull% %EpisodeTitle% %FileInfo%\n" +
            "BasePath:={%IsMovie% ? %BaseMoviePath% : %BaseTVShowPath% }\n" +
            "FileName:={%IsMovie% ? %MovieFileName% : %TVShowFileName% }\n" +
            "PathName:=%BasePath% %ShowTitle%";

    public boolean isAddToMylist() {
        return mAddToMylist;
    }

    public void setAddToMylist(boolean addToMylist) {
        mAddToMylist = addToMylist;
    }

    public boolean isAdvancedMode() {
        return mAdvancedMode;
    }

    public void setAdvancedMode(boolean advancedMode) {
        mAdvancedMode = advancedMode;
    }

    public String getTestString() {
        return mTestString;
    }

    public void setTestString(String testString) {
        mTestString = testString;
    }

    public String getTagSystemCode() {
        return mTagSystemCode;
    }

    public void setTagSystemCode(String tagSystemCode) {
        mTagSystemCode = tagSystemCode;
    }

    public boolean isMoveTypeUseFolder() {
        return mMoveTypeUseFolder;
    }

    public void setMoveTypeUseFolder(boolean moveTypeUseFolder) {
        mMoveTypeUseFolder = moveTypeUseFolder;
    }
}

package aniAdd.config;


/**
 * Created by Archie on 23.12.2015.
 */
public class XBMCDefaultNASConfiguration extends AniConfiguration {

    public XBMCDefaultNASConfiguration() {
        setEnableFileMove(true);
        setEnableFileRenaming(true);
        setMoveToFolder("");
        setOverwriteMLEntries(false);
        setRecursivelyDeleteEmptyFolders(false);
        setRenameFiles(false);
        setRenameRelatedFiles(false);
        setRenameTypeAniDBFileName(false);
        setSetStorageType(StorageType.INTERNAL);
        setFolderToLoad("/path/to/anime");
        setSetWatched(false);
        setAddToMylist(false);
        setAdvancedMode(false);
        setMoveTypeUseFolder(false);
        setDeleteDuplicateFiles(false);
        setTagSystemCode("BaseTVShowPath:=\"/volume1/Anime/TV Shows/\"                       #Set to your root folder for anime TV shows\n" +
                "BaseMoviePath:=\"/volume1/Anime/Movies/\"                          #Set to your root folder for anime movies\n" +
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
                "PathName:=%BasePath% %ShowTitle%");
    }
}

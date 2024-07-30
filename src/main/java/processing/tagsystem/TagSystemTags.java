package processing.tagsystem;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TagSystemTags {
    BaseTvShowPath("BaseTVShowPath"),
    BaseMoviePath("BaseMoviePath"),
    SeriesNameRomaji("ATr"),
    SeriesNameEnglish("ATe"),
    SeriesNameKanji("ATk"),
    SeriesNameSynonyms("ATs"),
    SeriesNameOther("ATo"),
    SeriesYearBegin("AYearBegin"),
    SeriesYearEnd("AYearEnd"),
    SeriesCategoryList("ACatList"),
    EpisodeNameRomaji("ETr"),
    EpisodeNameEnglish("ETe"),
    EpisodeNameKanji("ETk"),
    EpisodeAirDate("EAirDate"),
    GroupNameShort("GTs"),
    GroupNameLong("GTl"),
    FileCrc("FCrc"),
    FileAudioLanguage("FALng"),
    FileAudioCodec("FACodec"),
    FileSubtitleLanguage("FSLng"),
    FileVideoCodec("FVCodec"),
    FileVideoResolution("FVideoRes"),
    FileColorDepth("FColorDepth"),
    FileDuration("FDuration"),
    FileAnidbFilename("AniDBFN"),
    FileCurrentFilename("CurrentFN"),
    EpisodeNumber("EpNo"),
    EpisodeHiNumber("EpHiNo"),
    EpisodeCount("EpCount"),
    FileId("FId"),
    AnimeId("AId"),
    EpisodeId("EId"),
    GroupId("GId"),
    MyListId("LId"),
    OtherEpisodes("OtherEps"),
    Quality("Quality"),
    Source("Source"),
    Type("Type"),
    Watched("Watched"),
    Deprecated("Depr"),
    CrcOK("CrcOK"),
    CrcError("CrcErr"),
    Censored("Cen"),
    Uncensored("UnCen"),
    Version("Ver"),
    Ed2kHash("Ed2k");

    @Getter private final String tag;

}

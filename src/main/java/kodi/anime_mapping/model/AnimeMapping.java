package kodi.anime_mapping.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class AnimeMapping {
    String name;
    long aniDbId;
    Integer tvDbId;
    Integer episodeOffset;
    @Singular
    List<String> imdbIds;
    @Singular
    List<String> tmDbIds;
    String defaultTvDbSeason;
    @Builder.Default
    AnimeType type = AnimeType.SERIES;
    @Singular
    List<Mapping> mappings;
    SupplementalInfo supplementalInfo;

    public static enum AnimeType {
        SERIES, MOVIE, HENTAI, OVA, TVSPECIAL, MUSIC_VIDEO, WEB, OTHER;
    }
}

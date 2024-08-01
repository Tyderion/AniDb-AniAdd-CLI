package kodi.mapping.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class Anime {
    String name;
    long aniDbId;
    Long tvDbId;
    Integer episodeOffset;
    String imdbId;
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

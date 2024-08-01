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
    long tvDbId;
    Integer episodeOffset;
    String imdbId;
    String defaultTvDbSeason;
    @Singular
    List<Mapping> mappings;
    SupplementalInfo supplementalInfo;
}

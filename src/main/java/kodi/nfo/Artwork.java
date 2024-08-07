package kodi.nfo;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Artwork {
    String url;
    ArtworkType type;

    @Builder.Default
    int season = -1;

    public enum ArtworkType {
        SERIES_BANNER,
        SERIES_POSTER,
        SERIES_BACKGROUND,
        SEASON_BANNER,
        SEASON_POSTER,
        SEASON_BACKGROUND,
        CLEARART,
        CLEARLOGO
    }
}
package kodi.nfo;

import kodi.common.UniqueId;
import kodi.tvdb.TvDbArtworksResponse;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Builder
@Value
public class Series {
    String title;
    String originalTitle;

    int voteCount;
    double rating;
    String plot;
    boolean watched;

    @Singular
    List<UniqueId> uniqueIds;

    @Singular
    List<String> genres;
    @Builder.Default
    String tag = "anime";

//    LocalDate added;
    LocalDate premiered;
    int year;
    String studio;

    @Singular
    List<Actor> actors;

    @Singular
    List<Artwork> artworks;

    @Singular
    List<Artwork> fanarts;

    @Builder
    @Value
    public static class Artwork {
        String url;
        ArtworkType type;

        @Builder.Default
        int season = -1;
    }

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

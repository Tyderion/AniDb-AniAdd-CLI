package kodi.nfo;

import kodi.common.UniqueId;
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

    @Singular
    List<Rating> ratings;
    String plot;

    @Singular
    List<UniqueId> uniqueIds;

    @Singular
    List<String> genres;
    @Builder.Default
    String tag = "anime";

    LocalDate premiered;
    int year;
    String studio;

    String status;

    @Singular
    List<Actor> actors;

    @Singular
    List<Artwork> artworks;

    @Singular
    List<Artwork> fanarts;
}

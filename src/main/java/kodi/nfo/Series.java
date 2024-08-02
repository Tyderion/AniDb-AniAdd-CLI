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
}

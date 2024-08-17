package kodi.nfo.model;

import kodi.common.UniqueId;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.List;

@SuperBuilder
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Movie extends RootTag {
    String title;
    String originalTitle;

    @Singular List<Rating> ratings;

    String outline;
    String plot;
    String tagline;

    int runtimeInSeconds;

    String thumbnail;
    @Singular List<Artwork> fanarts;
    @Singular List<UniqueId> uniqueIds;
    @Singular List<String> tags;
    @Singular List<String> genres;
    @Singular List<String> credits;
    @Singular List<String> directors;
    String trailer;
    String studio;

    LocalDate lastPlayed;
    public boolean isWatched() {
        return lastPlayed != null;
    }
    Set set;
    LocalDate premiered;
    StreamDetails streamDetails;
    @Singular List<Actor> actors;

    @Builder
    @Value
    public static class Set {
        String name;
        String overview;
    }
}

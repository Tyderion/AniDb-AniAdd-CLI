package kodi.nfo;


import kodi.common.UniqueId;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@SuperBuilder
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Episode extends RootTag {
    String title;
    String originalTitle;
    String showTitle;

    @Singular
    List<Rating> ratings;

    int season;
    int episode;

    String plot;

    int runtimeInSeconds;

    @Singular
    List<UniqueId> uniqueIds;

    String thumbnail;

    @Singular
    List<String> genres;

    @Singular
    List<String> credits;

    @Singular
    List<String> directors;

    LocalDate premiered;

    StreamDetails streamDetails;
    LocalDate dateAdded;

    LocalDate lastPlayed;

    public boolean isWatched() {
        return lastPlayed != null;
    }


}

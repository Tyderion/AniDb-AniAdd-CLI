package kodi.nfo;


import kodi.common.UniqueId;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Builder
@Value
public class Episode {
    String title;
    String showTitle;

    int voteCount;
    double rating;

    int season;
    int episode;

    String plot;

    int runtimeInSeconds;
    boolean watched;

    @Singular
    List<UniqueId> uniqueIds;

    @Singular
    List<String> genres;

    @Singular
    List<String> credits;

    @Singular
    List<String> directors;

    LocalDate premiered;
    int year;
    String studio;

    StreamDetails streamDetails;
    LocalDate dateAdded;

    @Singular
    List<Actor> actors;

    @Value
    @Builder
    public static class StreamDetails {
        Audio audio;
        Video video;
        List<String> subtitles;
    }

    @Value
    @Builder
    public static class Audio {
        String codec;
        String language;
        int channels;
    }

    @Value
    @Builder
    public static class Video {
        String codec;
        double aspectRatio;
        String resolution;
        int durationInSeconds;
    }
}

package kodi.nfo;


import kodi.common.UniqueId;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    Path filePath;
    String fileExtension;
    String fileNameWithoutExtension;

    public String getFileName() {
        return STR."\{fileNameWithoutExtension}.\{fileExtension}";
    }

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
        int width;
        int height;
        int durationInSeconds;
    }
}

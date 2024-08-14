package kodi.nfo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class StreamDetails {
    Audio audio;
    Video video;
    List<String> subtitles;

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
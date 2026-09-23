package kodi.tmdb.requests;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.Optional;

@Data
public class TmDbMovieVideosResponse {
    private int id;

    @SerializedName("results")
    private java.util.List<Video> videos;

    @Data
    public static class Video {
        private String key;
        private String name;
        private String site;
        private int size;
        private Type type;
        private boolean official;

        @SerializedName("iso_639_1")
        private String language;


        public enum Type {
            @SerializedName("Trailer")
            TRAILER,
            @SerializedName("Teaser")
            TEASER,
            @SerializedName("Clip")
            CLIP
        }
    }

    public Optional<Video> getEnglishTrailer() {
        return videos
                .stream()
                .filter(video -> video.type == Video.Type.TRAILER)
                .filter(video -> video.site.equals("YouTube"))
                .filter(video -> video.official)
                .filter(video -> video.language.equals("en"))
                .findFirst();
    }


}

package kodi.tmdb;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.Comparator;
import java.util.Optional;

@Data
public class TmDbMovieTrailersResponse {
    private int id;

    @SerializedName("results")
    private java.util.List<Trailer> videos;

    @Data
    public static class Trailer {
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

    public Optional<Trailer> getEnglishTrailer() {
        return videos
                .stream()
                .filter(trailer -> trailer.type == Trailer.Type.TRAILER)
                .filter(trailer -> trailer.site.equals("YouTube"))
                .filter(trailer -> trailer.official)
                .filter(trailer -> trailer.language.equals("en"))
                .findFirst();
    }


}

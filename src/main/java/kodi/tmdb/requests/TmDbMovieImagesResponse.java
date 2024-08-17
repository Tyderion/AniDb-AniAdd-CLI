package kodi.tmdb.requests;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class TmDbMovieImagesResponse {
    private int id;

    private List<Image> backdrops;
    private List<Image> posters;

    @Data
    public static class Image {
        @SerializedName("file_path")
        private String filePath;

        @SerializedName("iso_639_1")
        private String language;

        @SerializedName("vote_count")
        private int voteCount;
        @SerializedName("vote_average")
        private double voteAverage;
    }
}

package kodi.tmdb.requests;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class TmDbMovieDetailsResponse {

    private int id;

    private String title;
    @SerializedName("original_title")
    private String originalTitle;

    @SerializedName("overview")
    private String plot;

    @SerializedName("vote_count")
    private int voteCount;

    @SerializedName("vote_average")
    private double voteAverage;
}

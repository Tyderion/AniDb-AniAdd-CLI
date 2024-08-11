package kodi.tmdb;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class TmDbMovieDetailsResponse {

    private int id;

    private String title;

    @SerializedName("overview")
    private String plot;

    @SerializedName("vote_count")
    private int voteCount;

    @SerializedName("vote_average")
    private double voteAverage;
}

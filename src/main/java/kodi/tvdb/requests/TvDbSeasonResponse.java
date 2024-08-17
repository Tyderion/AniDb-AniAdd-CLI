package kodi.tvdb.requests;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class TvDbSeasonResponse {
    int id;
    @SerializedName("status")
    SeriesStatus seriesStatus;
    List<Season> seasons;

    @Data
    public static class SeriesStatus {
        int id;
        @SerializedName("name")
        Status status;

        enum Status {
            @SerializedName("Continuing")
            CONTINUING,
            @SerializedName("Ended")
            ENDED,
            @SerializedName("Upcoming")
            UPCOMING,
            UNKNOWN;
        }
    }

    @Data
    public class Season {
        int id;
        int number;
        SeasonType type;
        @SerializedName("image")
        String seasonPoster;
    }

    @Data
    public class SeasonType {
        int id;
        Type type;

        enum Type {
            @SerializedName("official")
            OFFICIAL,
            @SerializedName("dvd")
            DVD,
            @SerializedName("absolute")
            ABSOLUT,
            @SerializedName("alternate")
            ALTERNATE;
        }
    }


}

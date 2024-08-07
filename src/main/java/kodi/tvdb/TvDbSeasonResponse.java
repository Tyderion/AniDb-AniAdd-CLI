package kodi.tvdb;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
public class TvDbSeasonResponse {
    int id;
    SeriesStatus status;
    List<Season> seasons;

    @Data
    public class SeriesStatus {
        int id;
        Status status;

        enum Status {
            @SerializedName("Continuing")
            CONTINUING,
            @SerializedName("Ended")
            ENDED,
            @SerializedName("Upcoming")
            UPCOMING;
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

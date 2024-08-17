package kodi.tvdb.requests;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TvDbEpisodesResponse {
    int id;
    @SerializedName("image")
    String seriesPoster;
    String name;

    List<Episode> episodes;

    @Data
    public class Episode {
        int id;
        int number;
        int absoluteNumber;
        int seasonNumber;
        String image;
        @SerializedName("overview")
        String plot;
        String name;
    }
}

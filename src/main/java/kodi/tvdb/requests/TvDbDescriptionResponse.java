package kodi.tvdb.requests;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class TvDbDescriptionResponse {
    @SerializedName("overview")
    String plot;
    String name;
}

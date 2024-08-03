package kodi.tvdb;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class TvDbDescriptionResponse {
    @SerializedName("overview")
    String plot;
}

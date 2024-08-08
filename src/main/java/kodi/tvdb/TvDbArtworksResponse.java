package kodi.tvdb;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class TvDbArtworksResponse {
    int id;
    List<Artwork> artworks;
    @Data
    public class Artwork {
        int id;
        String image;
        Type type;

        public enum Type {
            @SerializedName("1")
            SERIES_BANNER,
            @SerializedName("2")
            SERIES_POSTER,
            @SerializedName("3")
            SERIES_BACKGROUND,
            @SerializedName("5")
            SERIES_ICON,
            @SerializedName("6")
            SEASON_BANNER,
            @SerializedName("7")
            SEASON_POSTER,
            @SerializedName("8")
            SEASON_BACKGROUND,
            @SerializedName("10")
            SEASON_ICON,
            @SerializedName("11")
            EPISODE_SCREENCAP_16_9,
            @SerializedName("12")
            EPISODE_SCREENCAP_4_3,
            @SerializedName("13")
            ACTOR_PHOTO,
            @SerializedName("14")
            MOVIE_POSTER,
            @SerializedName("15")
            MOVIE_BACKGROUND,
            @SerializedName("16")
            MOVIE_BANNER,
            @SerializedName("18")
            MOVIE_ICON,
            @SerializedName("19")
            COMPANY_ICON,
            @SerializedName("20")
            SERIES_CINEMAGRAPH,
            @SerializedName("21")
            MOVIE_CINEMAGRAPH,
            @SerializedName("22")
            SERIES_CLEARART,
            @SerializedName("23")
            SERIES_CLEARLOGO,
            @SerializedName("24")
            MOVIE_CLEARART,
            @SerializedName("25")
            MOVIE_CLEARLOGO,
            @SerializedName("26")
            AWARD_ICON,
            @SerializedName("27")
            LIST_POSTER;
        }
    }


}

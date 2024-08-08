package kodi.tvdb;

import org.jetbrains.annotations.Nullable;
import retrofit2.Call;
import retrofit2.http.*;
public interface TVDbClient {
    @GET("series/{seriesId}/artworks")
    Call<TvDbResponse<TvDbArtworksResponse>> getArtworks(@Path("seriesId") int seriesId, @Query("lang") String lang, @Nullable @Query("type") TvDbArtworksResponse.Artwork.Type type);


    @GET("series/{seriesId}/episodes/official/eng")
    Call<TvDbResponse<TvDbEpisodesResponse>> getEpisodes(@Path("seriesId") int seriesId, @Query("page") int page);


    @GET("series/{seriesId}/extended?short=true")
    Call<TvDbResponse<TvDbSeasonResponse>> getSeasons(@Path("seriesId") int seriesId);

    @GET("series/{seriesId}/translations/eng")
    Call<TvDbResponse<TvDbDescriptionResponse>> getPlot(@Path("seriesId") int seriesId);

    @POST("login")
    Call<TvDbResponse<LoginResponse>> login(@Body LoginRequest loginRequest);


}

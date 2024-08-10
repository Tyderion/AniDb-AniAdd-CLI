package kodi.tmdb;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface TmDbClient {
    @GET("movie/{movieId}")
    Call<TmDbMovieDetailsResponse> getMovieDetails(@Path("movieId") int movieId);

    @GET("movie/{movieId}/images")
    Call<TmDbMovieImagesResponse> getArtworks(@Path("movieId") int movieId);

    @GET("movie/{movieId}/videos")
    Call<TmDbMovieTrailersResponse> getTrailers(@Path("movieId") int movieId);

}

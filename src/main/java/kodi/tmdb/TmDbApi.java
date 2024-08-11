package kodi.tmdb;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import utils.http.OkHttpClientFactory;

import java.util.concurrent.ExecutorService;

@Slf4j
public class TmDbApi {
    private final TmDbClient tmDbClient;
    private final OkHttpClient okHttpClient;
    private final String accessToken;

    public TmDbApi(String accessToken, ExecutorService executorService) {
        this.accessToken = accessToken;
        this.okHttpClient = httpClient(executorService);
        this.tmDbClient = initClient();
    }

    private OkHttpClient httpClient(ExecutorService executorService) {
        val httpClient = OkHttpClientFactory.createOkHttpClient(executorService);
        httpClient.addInterceptor(chain -> {
            val original = chain.request();
            val request = original.newBuilder()
                    .header("Authorization", STR."Bearer \{accessToken}")
                    .method(original.method(), original.body())
                    .build();
            return chain.proceed(request);
        });
        return httpClient.build();
    }

    public void getMovieInfo(int movieId, OnComplete onComplete) {
        val builder = MovieData.builder();
        tmDbClient.getMovieDetails(movieId).enqueue(new RequestCallback<>(builder, onComplete,
                data -> {
                    log.info("Received movie details");
                    builder.details(data);
                }, () -> {
            builder.details(null);
            log.error("Failed to get movie details");
        }));

        tmDbClient.getArtworks(movieId).enqueue(new RequestCallback<>(builder, onComplete,
                data -> {
                    log.info("Received movie artworks");
                    builder.images(data);
                }, () -> {
            builder.images(null);
            log.error("Failed to get movie artworks");
        }));

        tmDbClient.getTrailers(movieId).enqueue(new RequestCallback<>(builder, onComplete,
                data -> {
                    log.info("Received movie trailers");
                    builder.videos(data);
                }, () -> {
            builder.videos(null);
            log.error("Failed to get movie trailers");
        }));
    }

    private static class RequestCallback<T> extends utils.http.RequestCallback<T> {
        public RequestCallback(MovieData.MovieDataBuilder builder, OnComplete onComplete, OnResponse<T> onResponse, OnUnauthorized onFailure) {
            super(response -> {
                onResponse.received(response);
                if (builder.isComplete()) {
                    log.info("Movie complete");

                    onComplete.received(builder.build());
                }
            }, onFailure);
        }
    }

    private TmDbClient initClient() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)
                .build();

        return retrofit.create(TmDbClient.class);
    }

    public interface OnComplete {
        void received(MovieData data);
    }
}

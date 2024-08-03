package kodi.tvdb;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Log
public class TvDbImageDownloader {
    private final TVDbClient tvDbClient;
    private final OkHttpClient okHttpClient;
    private String token;

    public TvDbImageDownloader(String apiKey, ExecutorService executorService) {
        this.okHttpClient = httpClient(executorService);
        this.tvDbClient = initClient(apiKey);
    }

    public void test(int seriesId) {
        tvDbClient.getSeasons(seriesId).enqueue(new RequestCallback<>(data -> {
            List<TvDbSeasonResponse.Season> seasons = data.getSeasons();
            for (TvDbSeasonResponse.Season season : seasons) {
                log.info(STR."Season: \{season}");
            }
        }));
        tvDbClient.getArtworks(seriesId, "eng", null).enqueue(new RequestCallback<>(data -> {
            data.getArtworks().forEach(artwork -> {
                log.info(STR."Artwork: \{artwork}");
            });
        }));

        tvDbClient.getEpisodes(seriesId, 0).enqueue(new RequestCallback<>(data -> {
            data.getEpisodes().forEach(episode -> {
                log.info(STR."Episode: \{episode}");
            });
        }));

        tvDbClient.getPlot(seriesId).enqueue(new RequestCallback<>(data -> {
            log.info(STR."Description: \{data.getPlot()}");
        }));
    }


    private TVDbClient initClient(String apiKey) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api4.thetvdb.com/v4/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)
                .build();

        val client = retrofit.create(TVDbClient.class);
        try {
            val response = client.login(new LoginRequest(apiKey)).execute();
            if (response.isSuccessful()) {
                val body = response.body();
                if (body.getStatus() == TvDbResponse.Status.SUCCESS) {
                    token = body.getData().getToken();
                    return client;
                }
            }
            throw new RuntimeException(STR."Failed to login to TVDb: \{response.message()}");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private OkHttpClient httpClient(ExecutorService executorService) {
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        httpClient.setDispatcher$okhttp(new Dispatcher(executorService));
        httpClient.addInterceptor(chain -> {
            val original = chain.request();
            if (original.url().encodedPath().contains("login")) {
                return chain.proceed(original);
            }
            val request = original.newBuilder()
                    .header("Authorization", STR."Bearer \{token}")
                    .method(original.method(), original.body())
                    .build();
            return chain.proceed(request);
        });
        return httpClient.build();
    }

    @RequiredArgsConstructor
    private static class RequestCallback<T> implements Callback<TvDbResponse<T>> {

        final OnResponse<T> onResponse;

        @Override
        public void onResponse(Call<TvDbResponse<T>> call, Response<TvDbResponse<T>> response) {
            if (response.isSuccessful()) {
                val body = response.body();
                if (body != null && body.getStatus() == TvDbResponse.Status.SUCCESS) {
                    onResponse.onSuccess(body.getData());
                }
            }
        }

        @Override
        public void onFailure(Call<TvDbResponse<T>> call, Throwable t) {
            log.severe(STR."Failed to execute request: \{t.getMessage()}");
        }

        interface OnResponse<T> {
            void onSuccess(T data);
        }
    }
}

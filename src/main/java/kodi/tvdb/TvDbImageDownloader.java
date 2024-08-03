package kodi.tvdb;

import lombok.Data;
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

    public void getAllTvDbData(int seriesId, IAllDataCallback onReceive) {
        val requestsDone = new RequestsDone();
        val allData = TvDbAllData.builder();

        tvDbClient.getSeasons(seriesId).enqueue(new RequestCallback<>(data -> {
            allData.seasons(data.getSeasons());
            requestsDone.setSeasons(true);

            notify(onReceive, allData, requestsDone);
        }));

        getAllEpisodeData(seriesId, 0, requestsDone, allData, onReceive);

        tvDbClient.getArtworks(seriesId, "eng", null).enqueue(new RequestCallback<>(data -> {
            allData.artworks(data.getArtworks());
            requestsDone.setArtworks(true);
            notify(onReceive, allData, requestsDone);
        }));

        tvDbClient.getPlot(seriesId).enqueue(new RequestCallback<>(data -> {
            allData.plot(data.getPlot());
            requestsDone.setPlot(true);
            notify(onReceive, allData, requestsDone);
        }));

    }

    private void getAllEpisodeData(int seriesId, int page, RequestsDone requestsDone, TvDbAllData.TvDbAllDataBuilder allData, IAllDataCallback onReceive) {
        tvDbClient.getEpisodes(seriesId, page).enqueue(new FulLRequestCallback<>(data -> {
            val episodes = data.getData().getEpisodes();
            if (episodes.isEmpty()) {
                requestsDone.setEpisodes(true);
                notify(onReceive, allData, requestsDone);
                return;
            }
            data.getData().getEpisodes().forEach(allData::episode);
            if (data.getLinks().getPagesize() < data.getLinks().getTotalItems()) {
                getAllEpisodeData(seriesId, page + 1, requestsDone, allData, onReceive);
            }
        }));
    }

    private void notify(IAllDataCallback onReceive, TvDbAllData.TvDbAllDataBuilder allData, RequestsDone requestsDone) {
        if (requestsDone.allDone()) {
            onReceive.received(allData.build());
        }
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
    private static class FulLRequestCallback<T> implements Callback<TvDbResponse<T>> {

        final OnResponse<TvDbResponse<T>> onResponse;

        @Override
        public void onResponse(Call<TvDbResponse<T>> call, Response<TvDbResponse<T>> response) {
            if (response.isSuccessful()) {
                onResponse.onSuccess(response.body());
            }
        }

        @Override
        public void onFailure(Call<TvDbResponse<T>> call, Throwable t) {
            log.severe(STR."Failed to execute request: \{t.getMessage()}");
        }
    }

    private static class RequestCallback<T> extends FulLRequestCallback<T> {
        private RequestCallback(OnResponse<T> onResponse) {
            super(response -> {
                if (response != null && response.getStatus() == TvDbResponse.Status.SUCCESS) {
                    onResponse.onSuccess(response.getData());
                }
            });
        }
    }

    private interface OnResponse<T> {
        void onSuccess(T data);
    }

    public interface IAllDataCallback {
        void received(TvDbAllData data);
    }

    @Data
    private static class RequestsDone {
        boolean seasons;
        boolean artworks;
        boolean episodes;
        boolean plot;

        public boolean allDone() {
            return seasons && artworks && episodes && plot;
        }
    }
}

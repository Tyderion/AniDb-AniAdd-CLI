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
import java.util.Optional;
import java.util.concurrent.ExecutorService;

@Log
public class TvDbApi {
    private final TVDbClient tvDbClient;
    private final OkHttpClient okHttpClient;
    private final String apiKey;
    private String token;
    private boolean loggedIn;

    public TvDbApi(String apiKey, ExecutorService executorService) {
        this.apiKey = apiKey;
        this.okHttpClient = httpClient(executorService);
        this.tvDbClient = initClient(apiKey);
    }

    public void getAllTvDbData(int seriesId, IAllDataCallback onReceive) {
        log.info(STR."Getting all data for tvdb series \{seriesId}");
        val requestsDone = new RequestsDone();
        val allData = TvDbAllData.builder();

        if (!loggedIn) {
            onReceive.received(Optional.empty());
            return;
        }

        getAllEpisodeData(seriesId, 0, requestsDone, allData, onReceive);
        getSeasons(seriesId, requestsDone, allData, onReceive);
        getArtworks(seriesId, requestsDone, allData, onReceive);
        getPlot(seriesId, requestsDone, allData, onReceive);
    }

    private void getArtworks(int seriesId, RequestsDone requestsDone, TvDbAllData.TvDbAllDataBuilder allData, IAllDataCallback onReceive) {
        log.info(STR."Fetching artworks for series \{seriesId}");
        tvDbClient.getArtworks(seriesId, "eng", null).enqueue(new RequestCallback<>(data -> {
            allData.artworks(data.getArtworks());
            requestsDone.setArtworks(true);
            log.info(STR."Successfully fetched all \{data.getArtworks().size()} artworks for series \{seriesId}");
            notify(onReceive, allData, requestsDone);
        }, _ -> {
            login(tvDbClient, apiKey);
            getArtworks(seriesId, requestsDone, allData, onReceive);
        }));
    }

    private void getPlot(int seriesId, RequestsDone requestsDone, TvDbAllData.TvDbAllDataBuilder allData, IAllDataCallback onReceive) {
        log.info(STR."Fetching plot for series \{seriesId}");
        tvDbClient.getPlot(seriesId).enqueue(new RequestCallback<>(data -> {
            allData.plot(data.getPlot());
            requestsDone.setPlot(true);
            log.info(STR."Successfully fetched plot for series \{seriesId}");
            notify(onReceive, allData, requestsDone);
        }, _ -> {
            login(tvDbClient, apiKey);
            getPlot(seriesId, requestsDone, allData, onReceive);
        }));
    }

    private void getSeasons(int seriesId, RequestsDone requestsDone, TvDbAllData.TvDbAllDataBuilder allData, IAllDataCallback onReceive) {
        log.info(STR."Fetching seasons for series \{seriesId}");
        tvDbClient.getSeasons(seriesId).enqueue(new RequestCallback<>(data -> {
            allData.seasons(data.getSeasons());
            requestsDone.setSeasons(true);
            log.info(STR."Successfully fetched all \{data.getSeasons().size()} seasons for series \{seriesId}");
            notify(onReceive, allData, requestsDone);
        }, _ -> {
            login(tvDbClient, apiKey);
            getSeasons(seriesId, requestsDone, allData, onReceive);
        }));
    }

    private void getAllEpisodeData(int seriesId, int page, RequestsDone requestsDone, TvDbAllData.TvDbAllDataBuilder allData, IAllDataCallback onReceive) {
        log.info(STR."Fetching episodes page \{page} for series \{seriesId}");
        tvDbClient.getEpisodes(seriesId, page).enqueue(new FulLRequestCallback<>(data -> {
            data.getData().getEpisodes().forEach(allData::episode);
            if (data.getLinks().getPagesize() * (page + 1) > data.getLinks().getTotalItems()) {
                requestsDone.setEpisodes(true);
                log.info(STR."Successfully fetched all \{data.getLinks().getTotalItems()} episodes for series \{seriesId}");
                notify(onReceive, allData, requestsDone);
                return;
            } else {
                getAllEpisodeData(seriesId, page + 1, requestsDone, allData, onReceive);
            }
        }, _ -> {
            login(tvDbClient, apiKey);
            getAllEpisodeData(seriesId, page, requestsDone, allData, onReceive);
        }));
    }

    private void notify(IAllDataCallback onReceive, TvDbAllData.TvDbAllDataBuilder allData, RequestsDone requestsDone) {
        log.info(STR."Checking if all requests are done for series \{requestsDone}");
        if (requestsDone.allDone()) {
            onReceive.received(Optional.of(allData.build()));
        }
    }


    private TVDbClient initClient(String apiKey) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api4.thetvdb.com/v4/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)
                .build();

        val client = retrofit.create(TVDbClient.class);
        login(client, apiKey);
        return client;
    }

    private void login(TVDbClient client, String apiKey) {
        try {
            log.info("Logging in to TVDb");
            val response = client.login(new LoginRequest(apiKey)).execute();
            if (response.isSuccessful()) {
                val body = response.body();
                if (body.getStatus() == TvDbResponse.Status.SUCCESS) {
                    log.info("Successfully logged in to TVDb");
                    token = body.getData().getToken();
                    loggedIn = true;
                    return;
                }
            }
            log.warning(STR."Failed to login to TVDb: \{response.message()}");
            loggedIn = false;
        } catch (IOException e) {
            log.severe(STR."Failed to login to TVDb: \{e.getMessage()}");
            loggedIn = false;
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
        final OnResponse<Void> onUnauthorized;

        @Override
        public void onResponse(Call<TvDbResponse<T>> call, Response<TvDbResponse<T>> response) {
            if (response.isSuccessful()) {
                onResponse.received(response.body());
            }
            if (response.code() == 401) {
                onUnauthorized.received(null);
            }
        }

        @Override
        public void onFailure(Call<TvDbResponse<T>> call, Throwable t) {
            if (t.getMessage() != null && t.getMessage().contains("401")) {
                onUnauthorized.received(null);
            }
            log.severe(STR."Failed to execute request: \{t.getMessage()}");
        }
    }

    private static class RequestCallback<T> extends FulLRequestCallback<T> {
        private RequestCallback(OnResponse<T> onResponse, OnResponse<Void> onUnauthorized) {
            super(response -> {
                if (response != null && response.getStatus() == TvDbResponse.Status.SUCCESS) {
                    onResponse.received(response.getData());
                }
            }, onUnauthorized);
        }
    }

    private interface OnResponse<T> {
        void received(T data);
    }

    public interface IAllDataCallback {
        void received(Optional<TvDbAllData> data);
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

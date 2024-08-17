package kodi.tvdb;

import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

@Slf4j
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

    public void getTvSeriesData(int seriesId, ITvSeriesCallback onReceive) {
        log.info(STR."Getting all data for tvdb series \{seriesId}");
        val allData = TVSeriesData.builder().seriesId(seriesId);

        if (!loggedIn) {
            onReceive.received(null);
            return;
        }

        getAllEpisodeData(seriesId, 0, allData, onReceive);
        getSeasons(seriesId, allData, onReceive);
        getArtworks(seriesId, allData, onReceive);
        getPlot(seriesId, allData, onReceive);
    }

    private void getArtworks(int seriesId, TVSeriesData.TVSeriesDataBuilder builder, ITvSeriesCallback onReceive) {
        log.info(STR."Fetching artworks for series \{seriesId}");
        tvDbClient.getArtworks(seriesId, "eng", null).enqueue(new RequestCallback<>(builder, onReceive, data -> {
            builder.artworks(data);
            log.info(STR."Successfully fetched all \{data.getArtworks().size()} artworks for series \{seriesId}");
        }, () -> {
            login(tvDbClient, apiKey);
            getArtworks(seriesId, builder, onReceive);
        }));
    }

    private void getPlot(int seriesId, TVSeriesData.TVSeriesDataBuilder builder, ITvSeriesCallback onReceive) {
        log.info(STR."Fetching plot for series \{seriesId}");
        tvDbClient.getPlot(seriesId).enqueue(new RequestCallback<>(builder, onReceive, data -> {
            builder.description(data);
            log.info(STR."Successfully fetched plot for series \{seriesId}");
        }, () -> {
            login(tvDbClient, apiKey);
            getPlot(seriesId, builder, onReceive);
        }));
    }

    private void getSeasons(int seriesId, TVSeriesData.TVSeriesDataBuilder builder, ITvSeriesCallback onReceive) {
        log.info(STR."Fetching seasons for series \{seriesId}");
        tvDbClient.getSeasons(seriesId).enqueue(new RequestCallback<>(builder, onReceive, data -> {
            builder.seasons(data);
            log.info(STR."Successfully fetched all \{data.getSeasons().size()} seasons for series \{seriesId}");
        }, () -> {
            login(tvDbClient, apiKey);
            getSeasons(seriesId, builder, onReceive);
        }));
    }

    private void getAllEpisodeData(int seriesId, int page, TVSeriesData.TVSeriesDataBuilder builder, ITvSeriesCallback onReceive) {
        log.info(STR."Fetching episodes page \{page} for series \{seriesId}");
        tvDbClient.getEpisodes(seriesId, page).enqueue(new RootRequestCallback<>(builder, onReceive, data -> {
            val hasMore = data.getLinks().getPagesize() * (page + 1) < data.getLinks().getTotalItems();
            builder.episodes(data.getData(), !hasMore);
            if (hasMore) {
                getAllEpisodeData(seriesId, page + 1, builder, onReceive);
            } else {
                log.info(STR."Successfully fetched all \{data.getLinks().getTotalItems()} episodes for series \{seriesId}");
            }
        }, () -> {
            login(tvDbClient, apiKey);
            getAllEpisodeData(seriesId, page, builder, onReceive);
        }) {
        });
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
            log.warn(STR."Failed to login to TVDb: \{response.message()}");
            loggedIn = false;
        } catch (IOException e) {
            log.error(STR."Failed to login to TVDb: \{e.getMessage()}");
            loggedIn = false;
        }
    }

    private OkHttpClient httpClient(ExecutorService executorService) {
        val httpClient = new OkHttpClient.Builder();
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

    private static class RequestCallback<T> extends RootRequestCallback<T> {
        private RequestCallback(TVSeriesData.TVSeriesDataBuilder builder, ITvSeriesCallback onComplete, OnResponse<T> onResponse, OnUnauthorized onUnauthorized) {
            super(builder, onComplete, response -> {
                if (response != null && response.getStatus() == TvDbResponse.Status.SUCCESS) {
                    onResponse.received(response.getData());
                }
            }, onUnauthorized);
        }
    }

    @Slf4j
    private static class RootRequestCallback<T> extends utils.http.RequestCallback<TvDbResponse<T>> {
        private RootRequestCallback(TVSeriesData.TVSeriesDataBuilder builder, ITvSeriesCallback onComplete, OnResponse<TvDbResponse<T>> onResponse, OnUnauthorized onUnauthorized) {
            super(response -> {
                if (response != null && response.getStatus() == TvDbResponse.Status.SUCCESS) {
                    onResponse.received(response);
                    if (builder.isComplete()) {
                        val data = builder.build();
                        log.trace(STR."All data for series \{data.getSeriesId()} received");
                        onComplete.received(data);
                    }
                }
            }, onUnauthorized);
        }
    }


    public interface ITvSeriesCallback {
        void received(TVSeriesData data);
    }
}

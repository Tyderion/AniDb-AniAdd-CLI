package kodi.tvdb;

import kodi.tvdb.requests.LoginRequest;
import kodi.tvdb.requests.TvDbResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.Dispatcher;
import org.jetbrains.annotations.NotNull;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

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

        val pending = new PendingRequests(allData, onReceive);
        getAllEpisodeData(seriesId, 0, allData, pending);
        getSeasons(seriesId, allData, pending);
        getArtworks(seriesId, allData, pending);
        getPlot(seriesId, allData, pending);
    }

    private void getArtworks(int seriesId, TVSeriesData.TVSeriesDataBuilder builder, PendingRequests pending) {
        log.info(STR."Fetching artworks for series \{seriesId}");
        pending.started();
        tvDbClient.getArtworks(seriesId, "eng", null).enqueue(new RequestCallback<>(pending, data -> {
            builder.artworks(data);
            log.info(STR."Successfully fetched all \{data.getArtworks().size()} artworks for series \{seriesId}");
        }, () -> {
            login(tvDbClient, apiKey);
            getArtworks(seriesId, builder, pending);
        }));
    }

    private void getPlot(int seriesId, TVSeriesData.TVSeriesDataBuilder builder, PendingRequests pending) {
        log.info(STR."Fetching plot for series \{seriesId}");
        pending.started();
        tvDbClient.getPlot(seriesId).enqueue(new RequestCallback<>(pending, data -> {
            builder.description(data);
            log.info(STR."Successfully fetched plot for series \{seriesId}");
        }, () -> {
            login(tvDbClient, apiKey);
            getPlot(seriesId, builder, pending);
        }));
    }

    private void getSeasons(int seriesId, TVSeriesData.TVSeriesDataBuilder builder, PendingRequests pending) {
        log.info(STR."Fetching seasons for series \{seriesId}");
        pending.started();
        tvDbClient.getSeasons(seriesId).enqueue(new RequestCallback<>(pending, data -> {
            builder.seasons(data);
            log.info(STR."Successfully fetched all \{data.getSeasons().size()} seasons for series \{seriesId}");
        }, () -> {
            login(tvDbClient, apiKey);
            getSeasons(seriesId, builder, pending);
        }));
    }

    private void getAllEpisodeData(int seriesId, int page, TVSeriesData.TVSeriesDataBuilder builder, PendingRequests pending) {
        log.info(STR."Fetching episodes page \{page} for series \{seriesId}");
        pending.started();
        tvDbClient.getEpisodes(seriesId, page).enqueue(new RootRequestCallback<>(pending, data -> {
            val hasMore = data.getLinks().getPagesize() * (page + 1) < data.getLinks().getTotalItems();
            builder.episodes(data.getData(), !hasMore);
            if (hasMore) {
                getAllEpisodeData(seriesId, page + 1, builder, pending);
            } else {
                log.info(STR."Successfully fetched all \{data.getLinks().getTotalItems()} episodes for series \{seriesId}");
            }
        }, () -> {
            login(tvDbClient, apiKey);
            getAllEpisodeData(seriesId, page, builder, pending);
        }));
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

    /**
     * Tracks the requests fanned out for one series. Every request registers via started() before it
     * is enqueued and reports via completedOne() on any terminal outcome (response or failure); a
     * follow-up request (pagination, re-auth retry) registers inside the handler of its predecessor,
     * before that predecessor reports, so the count can only reach zero once nothing is in flight.
     */
    @Slf4j
    @RequiredArgsConstructor
    private static class PendingRequests {
        private final TVSeriesData.TVSeriesDataBuilder builder;
        private final ITvSeriesCallback onComplete;
        private final AtomicInteger inFlight = new AtomicInteger();

        void started() {
            inFlight.incrementAndGet();
        }

        void completedOne() {
            if (inFlight.decrementAndGet() > 0) {
                return;
            }
            if (builder.isComplete()) {
                val data = builder.build();
                log.trace(STR."All data for series \{data.getSeriesId()} received");
                onComplete.received(data);
            } else {
                log.error(STR."All TVDb requests for series \{builder.getSeriesId()} finished but data is incomplete, missing: \{builder.missingParts()}");
                onComplete.received(null);
            }
        }
    }

    private static class RequestCallback<T> extends RootRequestCallback<T> {
        private RequestCallback(PendingRequests pending, OnResponse<T> onResponse, OnUnauthorized onUnauthorized) {
            super(pending, response -> {
                if (response != null && response.getStatus() == TvDbResponse.Status.SUCCESS) {
                    onResponse.received(response.getData());
                }
            }, onUnauthorized);
        }
    }

    private static class RootRequestCallback<T> extends utils.http.RequestCallback<TvDbResponse<T>> {
        private final PendingRequests pending;

        private RootRequestCallback(PendingRequests pending, OnResponse<TvDbResponse<T>> onResponse, OnUnauthorized onUnauthorized) {
            super(response -> {
                if (response != null && response.getStatus() == TvDbResponse.Status.SUCCESS) {
                    onResponse.received(response);
                }
            }, onUnauthorized);
            this.pending = pending;
        }

        @Override
        public void onResponse(@NotNull Call<TvDbResponse<T>> call, @NotNull Response<TvDbResponse<T>> response) {
            super.onResponse(call, response);
            pending.completedOne();
        }

        @Override
        public void onFailure(@NotNull Call<TvDbResponse<T>> call, @NotNull Throwable t) {
            super.onFailure(call, t);
            pending.completedOne();
        }
    }


    public interface ITvSeriesCallback {
        void received(TVSeriesData data);
    }
}

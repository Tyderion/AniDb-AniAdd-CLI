package kodi.tmdb;

import lombok.val;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import utils.http.OkHttpClientFactory;
import utils.http.RequestCallback;

import java.util.concurrent.ExecutorService;

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

    private TmDbClient initClient() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)
                .build();

        return retrofit.create(TmDbClient.class);
    }
}

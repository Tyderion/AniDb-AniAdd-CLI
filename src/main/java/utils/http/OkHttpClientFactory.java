package utils.http;

import lombok.val;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;

public class OkHttpClientFactory {
    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);
    private static final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(log::debug);

    static {
        if (log.isTraceEnabled()) {
            loggingInterceptor.level(HttpLoggingInterceptor.Level.BODY);
        } else if (log.isDebugEnabled()) {
            loggingInterceptor.level(HttpLoggingInterceptor.Level.BASIC);
        }
    }

    public static OkHttpClient.Builder createOkHttpClient(ExecutorService requestExecutorService) {
        val httpClient = new OkHttpClient.Builder();
        httpClient.addNetworkInterceptor(loggingInterceptor);
        httpClient.setDispatcher$okhttp(new Dispatcher(requestExecutorService));
        return httpClient;
    }
}

package kodi;

import kodi.tvdb.TvDbAllData;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.java.Log;
import lombok.val;
import okhttp3.Dispatcher;
import okhttp3.OkHttp;
import okhttp3.OkHttpClient;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

@Log
public class DownloadHelper {
    private final OkHttpClient client;

    public DownloadHelper(ExecutorService executorService) {
        val httpClient = new OkHttpClient.Builder();
        httpClient.setDispatcher$okhttp(new Dispatcher(executorService));
        client = httpClient.build();
    }

    public void downloadToFile(String url, Path path) {
        try {
            val request = new okhttp3.Request.Builder().url(url).build();
            val response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException(STR."Failed to download file: \{response}");
            }
            FileOutputStream fos = new FileOutputStream(path.toFile());
            fos.write(response.body().bytes());
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

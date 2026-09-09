package utils.http;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.OkHttpClient;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

@Slf4j
public class DownloadHelper {
    private final OkHttpClient client;

    public DownloadHelper(ExecutorService executorService) {
        client = OkHttpClientFactory.createOkHttpClient(executorService).build();
    }

    public String downloadToString(String url) {
        log.info(STR."Content at \{url} will be downloaded");
        try {
            val request = new okhttp3.Request.Builder().url(url).build();
            val response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException(STR."Failed to download content: \{response}");
            }
            log.trace(STR."Downloaded content from \{url}");
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void downloadToFile(String url, Path path) {
        log.info(STR."File at \{url} will be saved to \{path}");
        try {
            val request = new okhttp3.Request.Builder().url(url).build();
            val response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException(STR."Failed to download file: \{response}");
            }
            log.trace(STR."Downloading file from \{url} to \{path}");
            Files.createDirectories(path.getParent());
            FileOutputStream fos = new FileOutputStream(path.toFile());
            fos.write(response.body().bytes());
            fos.close();
            log.trace(STR."Downloaded file from \{url} to \{path}");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
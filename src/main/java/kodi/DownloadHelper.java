package kodi;

import kodi.tvdb.TvDbAllData;
import lombok.extern.java.Log;
import lombok.val;
import okhttp3.OkHttpClient;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

@Log
public class DownloadHelper {

    public static void downloadToFile(String url, Path path) {
        try {
            log.info(STR."Downloading file from \{url} to \{path}");
            val client = new OkHttpClient();
            val request = new okhttp3.Request.Builder().url(url).build();
            val response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException(STR."Failed to download file: \{response}");
            }
            FileOutputStream fos = new FileOutputStream(path.toFile());
            fos.write(response.body().bytes());
            fos.close();
            client.dispatcher().executorService().shutdownNow();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

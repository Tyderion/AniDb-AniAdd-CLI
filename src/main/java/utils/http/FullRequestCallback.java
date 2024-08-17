package utils.http;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Slf4j
@RequiredArgsConstructor
public abstract class FullRequestCallback<T> implements Callback<T> {

    final OnResponse<T> onResponse;
    final OnResponse<Void> onUnauthorized;

    @Override
    public void onResponse(@NotNull Call<T> _call, Response<T> response) {
        if (response.isSuccessful()) {
            onResponse.received(response.body());
        }
        if (response.code() == 401) {
            onUnauthorized.received(null);
        }
    }

    @Override
    public void onFailure(@NotNull Call<T> call, Throwable t) {
        if (t.getMessage() != null && t.getMessage().contains("401")) {
            onUnauthorized.received(null);
        }
        log.error(STR."Failed to execute request: \{t.getMessage()}");
    }

    public interface OnResponse<T> {
        void received(T data);
    }
}
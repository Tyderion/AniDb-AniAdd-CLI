package utils.http;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Slf4j
@RequiredArgsConstructor
public class RequestCallback<T> implements Callback<T> {

    final OnResponse<T> onResponse;
    final OnUnauthorized onUnauthorized;

    @Override
    public void onResponse(@NotNull Call<T> _call, Response<T> response) {
        if (response.isSuccessful()) {
            if (onResponse != null) {
                onResponse.received(response.body());
            }
        }
        if (response.code() == 401) {
            if (onUnauthorized != null) {
                onUnauthorized.unauthorized();
            }
        }
    }

    @Override
    public void onFailure(@NotNull Call<T> call, Throwable t) {
        if (t.getMessage() != null && t.getMessage().contains("401")) {
            if (onUnauthorized != null) {
                onUnauthorized.unauthorized();
            }
        }
        log.error(STR."Failed to execute request: \{t.getMessage()}");
    }

    public interface OnResponse<T> {
        void received(T data);
    }

    public interface OnUnauthorized {
        void unauthorized();
    }
}
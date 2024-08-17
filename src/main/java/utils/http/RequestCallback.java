package utils.http;


class RequestCallback<T> extends FullRequestCallback<T> {

    protected RequestCallback(OnResponse<T> onResponse, OnResponse<Void> onUnauthorized) {
        super(onResponse, onUnauthorized);
    }
}
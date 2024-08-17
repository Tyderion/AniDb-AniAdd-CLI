package kodi.tvdb.requests;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class TvDbResponse<T> {
    private Status status;
    private String message;
    private T data;

    private Links links;

    public enum Status {
        @SerializedName("success")
        SUCCESS,
        @SerializedName("failure")
        FAILURE
    }

    @Data
    public class Links {

        @SerializedName("total_items")
        int totalItems;
        @SerializedName("page_size")
        int pagesize;
    }
}

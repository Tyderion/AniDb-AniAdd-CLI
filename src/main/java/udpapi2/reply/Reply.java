package udpapi2.reply;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class Reply {
    String fullMessage;
    String fullTag;
    ReplyStatus replyStatus;
    Integer queryId;
    @Singular("value")
    List<String> responseData;

    public boolean isFatal() {
        return replyStatus.isFatal();
    }
}

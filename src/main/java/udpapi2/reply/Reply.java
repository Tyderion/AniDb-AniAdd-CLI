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
    Integer replyStatus;
    @Singular("value")
    List<String> responseData;
}

package udpapi2.command;

import lombok.*;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder(toBuilder = true)
@Value
@NonFinal
public class Command {
    String action;
    String identifier;
    String tag;
    boolean needsLogin;
    @Singular
    Map<String, String> parameters;
    Integer queryId;


    public String getFullTag() {
        val tagValue = tag == null ? "" : STR.":\{tag}";
        return STR."\{identifier}\{tagValue}-\{queryId}";
    }

    public String toString(String session) {
        StringBuilder cmdStr;
        cmdStr = new StringBuilder(STR."\{action} tag=\{getFullTag()}");

        if (session != null) {
            cmdStr.append("&s=").append(session);
        }

        for (Map.Entry<String, String> arg : parameters.entrySet()) {
            cmdStr.append("&").append(arg.getKey()).append("=").append(arg.getValue());
        }

        return cmdStr.toString();
    }
}

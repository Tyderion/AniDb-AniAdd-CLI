package udpapi.command;

import lombok.Getter;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import lombok.val;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuperBuilder(toBuilder = true)
@Value
@NonFinal
public class Command {
    String action;
    String identifier;
    Integer tag;
    boolean needsLogin;
    @Singular Map<String, String> parameters;
    Integer queryId;


    public String getFullTag() {
        val tagValue = tag == null ? "" : STR.":\{tag}";
        return STR."\{identifier}\{tagValue}-\{queryId}";
    }

    protected Set<String> getSensitiveParameters() {
        return Set.of();
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

    public String toString() {
        val sensitiveParameters = this.getSensitiveParameters();
        val sanitizedParameters = this.getParameters().entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> sensitiveParameters.contains(entry.getKey()) ? "***" : entry.getValue()));
        return STR."Command(action=\{this.getAction()}, identifier=\{this.getIdentifier()}, tag=\{this.getTag()}, needsLogin=\{this.isNeedsLogin()}, parameters=\{sanitizedParameters}, queryId=\{this.getQueryId()})";
    }
}

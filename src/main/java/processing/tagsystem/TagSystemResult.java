package processing.tagsystem;

import org.jetbrains.annotations.NotNull;

public record TagSystemResult(String PathName, @NotNull String FileName) {
}

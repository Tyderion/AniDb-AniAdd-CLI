package processing;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface IFileHandler {
    boolean renameFile(@NotNull Path from, @NotNull Path to);
    void deleteFile(@NotNull Path path);
}

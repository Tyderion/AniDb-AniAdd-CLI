package processing;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface IFileRenamer {
    boolean renameFile(@NotNull Path from, @NotNull Path to);
}

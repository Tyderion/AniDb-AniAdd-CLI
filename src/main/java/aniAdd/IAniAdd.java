package aniAdd;

import config.CliConfiguration;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface IAniAdd {

    void ProcessDirectory(Path directory);

    void MarkFileAsWatched(@NotNull Path path);

    CliConfiguration getConfiguration();

    void Stop();
}

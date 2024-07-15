package aniAdd;

import aniAdd.config.AniConfiguration;
import org.jetbrains.annotations.NotNull;

public interface IAniAdd {

    void ProcessDirectory(String directory);

    void MarkFileAsWatched(@NotNull String path);

    AniConfiguration getConfiguration();

    void Stop();
}

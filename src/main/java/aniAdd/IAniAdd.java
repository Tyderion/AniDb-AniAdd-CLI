package aniAdd;

import aniAdd.config.AniConfiguration;

public interface IAniAdd {

    void ProcessDirectory(String directory);

    void MarkFileAsWatched(String path);

    AniConfiguration getConfiguration();

    void Stop();
}

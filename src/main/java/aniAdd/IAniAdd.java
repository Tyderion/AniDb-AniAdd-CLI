package aniAdd;

import aniAdd.Modules.IModule;
import aniAdd.config.AniConfiguration;

public interface IAniAdd extends Communication {
    <T extends IModule> T GetModule(Class<T> modName);

    void ProcessDirectory(String directory);

    void MarkFileAsWatched(String path);

    AniConfiguration getConfiguration();

    void Stop();
}

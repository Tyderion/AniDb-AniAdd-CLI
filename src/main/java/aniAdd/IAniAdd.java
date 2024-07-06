package aniAdd;

import aniAdd.Modules.IModule;

public interface IAniAdd extends Communication {
    <T extends IModule> T GetModule(Class<T> modName);

    void ProcessDirectory(String directory);
}

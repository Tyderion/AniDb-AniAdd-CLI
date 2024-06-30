package aniAdd;

import aniAdd.Modules.IModule;
import java.util.Collection;

public interface IAniAdd extends Communication {
    <T extends IModule> T GetModule(Class<T> modName);
    Collection<IModule> GetModules();
}

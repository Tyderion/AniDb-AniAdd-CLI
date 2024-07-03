package aniAdd.Modules;

import aniAdd.*;
import aniAdd.config.AniConfiguration;


public interface IModule extends Communication {
    String ModuleName();

    eModState ModState();

    void Initialize(IAniAdd aniAdd, AniConfiguration configuration);

    void Terminate();

    enum eModState {New, Initializing, Initialized, Terminating, Terminated}
}

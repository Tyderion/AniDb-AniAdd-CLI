package processing;

import aniAdd.config.AniConfiguration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import processing.tagsystem.TagSystemTags;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class FileInfo {

    private final EnumSet<eAction> actionsTodo = EnumSet.of(eAction.Process);
    private final EnumSet<eAction> actionsDone = EnumSet.noneOf(eAction.class);
    private final EnumSet<eAction> actionsError = EnumSet.noneOf(eAction.class);
    @Getter private final Map<TagSystemTags, String> data = new HashMap<>();

    @Getter private final File file;

    @Getter private final int id;

    @Getter @Setter private File renamedFile;

    @Getter @Setter private Boolean watched;

    @Getter @Setter private boolean served;

    @Getter @Setter private boolean isFinal;

    @Getter @Setter private AniConfiguration configuration;

    public enum eAction {Process, FileCmd, MyListCmd, VoteCmd, Rename,}

    public void actionDone(eAction action) {
        actionsTodo.remove(action);
        actionsDone.add(action);
    }

    public boolean isActionDone(eAction action) {
        return actionsDone.contains(action);
    }

    public void addTodo(eAction action) {
        actionsTodo.add(action);
    }

    public boolean isActionTodo(eAction action) {
        return actionsTodo.contains(action);
    }

    public void actionFailed(eAction action) {
        actionsTodo.remove(action);
        actionsError.add(action);
    }
}

package processing;

import aniAdd.config.AniConfiguration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import processing.tagsystem.TagSystemTags;

import java.io.File;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class FileInfo {

    private final EnumSet<FileAction> actionsTodo = EnumSet.of(FileAction.Process);
    private final EnumSet<FileAction> actionsDone = EnumSet.noneOf(FileAction.class);
    private final EnumSet<FileAction> actionsError = EnumSet.noneOf(FileAction.class);
    @Getter private final Map<TagSystemTags, String> data = new HashMap<>();
    @Getter private final File file;
    @Getter private final int id;
    @Getter @Setter private Path renamedFile;
    @Getter @Setter private Boolean watched;
    @Getter @Setter private boolean hashed;
    @Getter @Setter private boolean isFinal;
    @Getter @Setter private AniConfiguration configuration;

    public enum FileAction {Process, FileCmd, MyListCmd, VoteCmd, Rename,}

    public void actionDone(FileAction action) {
        actionsTodo.remove(action);
        actionsDone.add(action);
    }

    public boolean isActionDone(FileAction action) {
        return actionsDone.contains(action);
    }
    
    public void addTodo(FileAction action) {
        actionsTodo.add(action);
    }

    public boolean isActionTodo(FileAction action) {
        return actionsTodo.contains(action);
    }

    public void actionFailed(FileAction action) {
        actionsTodo.remove(action);
        actionsError.add(action);
    }
}

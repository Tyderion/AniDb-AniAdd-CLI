package processing;

import aniAdd.config.AniConfiguration;
import cache.entities.AniDBFileData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.val;
import processing.tagsystem.TagSystemTags;

import java.io.File;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class FileInfo {

    private final EnumSet<FileAction> actionsDone = EnumSet.noneOf(FileAction.class);
    private final EnumSet<FileAction> actionsError = EnumSet.noneOf(FileAction.class);
    private final EnumSet<FileAction> actionsInProcess = EnumSet.noneOf(FileAction.class);
    @Getter private final Map<TagSystemTags, String> data = new HashMap<>();
    @Getter private final File file;
    @Getter private final int id;
    @Getter @Setter private Path renamedFile;
    @Getter @Setter private Boolean watched;
    @Getter @Setter private boolean hashed;
    @Getter @Setter private boolean isFinal;
    @Getter @Setter private AniConfiguration configuration;
    @Getter @Setter private boolean isCached = false;

    public enum FileAction {Init, HashFile, FileCmd, MyListAddCmd, Rename,}

    public void startAction(FileAction action) {
        actionsInProcess.add(action);
    }

    public boolean isActionInProcess(FileAction action) {
        return actionsInProcess.contains(action);
    }

    public void actionDone(FileAction action) {
        actionsInProcess.remove(action);
        actionsDone.add(action);
    }

    public boolean isActionDone(FileAction action) {
        return actionsDone.contains(action);
    }

    public void actionFailed(FileAction action) {
        actionsInProcess.remove(action);
        actionsError.add(action);
    }

    public boolean hasActionFailed(FileAction action) {
        return actionsError.contains(action);
    }

    public boolean allDone() {
        return actionsInProcess.isEmpty();
    }

    public String getEd2k() {
        return data.get(TagSystemTags.Ed2kHash);
    }

    public AniDBFileData toAniDBFileData() {
        val builder = AniDBFileData.builder()
                .ed2k(data.get(TagSystemTags.Ed2kHash))
                .tags(data);

        if (renamedFile != null) {
            builder.fileName(renamedFile.getFileName().toString());
            builder.size(renamedFile.toFile().length());
            builder.folderName(renamedFile.getParent().getFileName().toString());
        } else {
            builder.fileName(file.getName());
            builder.size(getFile().length());
            builder.folderName(file.getParentFile().getName());

        }
        return builder.build();

    }
}

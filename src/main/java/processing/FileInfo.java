package processing;

import cache.entities.AniDBFileData;
import config.blocks.FileConfig;
import config.blocks.MyListConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import processing.tagsystem.TagSystemTags;

import java.io.File;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class FileInfo {

    private final EnumSet<FileAction> actionsDone = EnumSet.noneOf(FileAction.class);
    private final EnumSet<FileAction> actionsError = EnumSet.noneOf(FileAction.class);
    private final EnumSet<FileAction> actionsInProcess = EnumSet.noneOf(FileAction.class);
    @Getter private final Map<TagSystemTags, String> data = new HashMap<>();
    @Getter private final File file;
    @Getter private final int id;
    @Getter private final long fileSize;
    private final String originalFileName;
    private final String originalFolder;
    @Getter private Path renamedFile;
    private String renamedFileName;
    private String renamedFolder;
    @Getter private final Boolean watched;
    @Getter @Setter private boolean hashed;

    @Accessors(fluent = true)
    @Getter
    private final FileConfig config;


    public FileInfo(File file, int id, Boolean watched, FileConfig config) {
        this.file = file;
        this.id = id;
        this.fileSize = file.length();
        this.originalFileName = file.getName();
        this.originalFolder = file.getParentFile().getName();
        this.watched = watched;
        this.config = config;
    }

    public void setRenamedFile(Path renamedFile) {
        this.renamedFile = renamedFile;
        this.renamedFileName = renamedFile.getFileName().toString();
        this.renamedFolder = renamedFile.getParent().getFileName().toString();
    }

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

        builder.size(fileSize);
        if (renamedFile != null) {
            builder.fileName(renamedFileName);
            builder.folderName(renamedFolder);
        } else {
            builder.fileName(originalFileName);
            builder.folderName(originalFolder);

        }
        return builder.build();
    }
}

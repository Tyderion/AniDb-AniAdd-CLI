package processing;

import cache.entities.AniDBFileData;
import config.blocks.FileConfig;
import kodi.nfo.model.Movie;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import processing.tagsystem.TagSystemTags;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
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
    /**
     * The file on disk, once it is no longer the one AniDB knows: a locally re-encoded copy. Identity
     * (ed2k hash and size) keeps describing the original release, so AniDB and MyList stay correct,
     * while every filesystem operation works on this.
     */
    @Getter private Path transcodedFile;
    /** ed2k hash and CRC32 of the bytes actually on disk, which differ from identity after a transcode. */
    @Getter @Setter private String localEd2k;
    @Getter @Setter private String localCrc32;
    /** Set when this file was produced locally from another one, so its identity came from the hash mapping. */
    @Getter @Setter private boolean mapped;
    private Long identitySize;
    @Getter private final Boolean watched;
    @Getter @Setter private boolean hashed;
    @Getter @Setter private LocalDateTime watchedDate;
    /** The file landed somewhere new or got metadata Kodi has not seen yet, so a library scan would pick up something */
    @Getter @Setter private boolean libraryChanged;
    /** Reached the end of the pipeline. Unlike {@link #allDone()} this is false before the first action starts. */
    @Getter @Setter private boolean finished;

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

    public void setTranscodedFile(Path transcodedFile) {
        this.transcodedFile = transcodedFile;
    }

    /**
     * @return the file to read, move and rename. The original file unless a transcode replaced it.
     */
    public File getWorkingFile() {
        return transcodedFile != null ? transcodedFile.toFile() : file;
    }

    /**
     * The size AniDB identifies this file by, which is the original's size for a converted file.
     */
    public long getIdentitySize() {
        return identitySize != null ? identitySize : fileSize;
    }

    public void setIdentity(String ed2k, long size) {
        data.put(TagSystemTags.Ed2kHash, ed2k);
        this.identitySize = size;
    }

    public void setRenamedFile(Path renamedFile) {
        this.renamedFile = renamedFile;
        this.renamedFileName = renamedFile.getFileName().toString();
        this.renamedFolder = renamedFile.getParent().getFileName().toString();
    }

    public enum FileAction {Init, HashFile, FileCmd, Transcode, MyListAddCmd, VoteCmd, Rename, LoadWatchedState, GenerateKodiMetadata}

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

    public Path getFinalFilePath() {
        return renamedFile != null ? renamedFile : getWorkingFile().toPath();
    }

    public String getEd2k() {
        return data.get(TagSystemTags.Ed2kHash);
    }

    public long getAniDbFileId() {
        return Long.parseLong(data.get(TagSystemTags.FileId));
    }

    public Movie.MovieBuilder toMovie() {
        val movie = toAniDBFileData().toMovie();
        movie.filePath(getFinalFilePath());
        return movie;
    }

    public AniDBFileData toAniDBFileData() {
        val builder = AniDBFileData.builder()
                .ed2k(data.get(TagSystemTags.Ed2kHash))
                .tags(data);

        builder.size(getIdentitySize());
        if (renamedFile != null) {
            builder.fileName(renamedFileName);
            builder.folderName(renamedFolder);
        } else {
            builder.fileName(getWorkingFile().getName());
            builder.folderName(getWorkingFile().getParentFile().getName());

        }
        return builder.build();
    }
}

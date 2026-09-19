package processing;

import config.blocks.MoveConfig;
import config.blocks.RenameConfig;
import config.blocks.TagsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import processing.tagsystem.TagSystem;
import processing.tagsystem.TagSystemResult;
import processing.tagsystem.TagSystemTags;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class FileRenamer {

    private final IFileHandler fileHandler;
    private final TagsConfig tagsConfig;

    public boolean renameFile(FileInfo procFile) {
        val moveConfig = procFile.config().move();
        val renameConfig = procFile.config().rename();
        try {
            val target = targetPath(procFile, false);
            if (target.isEmpty()) {
                return false;
            }
            val targetFilePath = target.get();
            val currentPath = procFile.getWorkingFile().toPath().toAbsolutePath();

            // Checked before existence: a file that already carries its correct name exists at its own target,
            // and must not be mistaken for a duplicate of itself.
            if (targetFilePath.toAbsolutePath().equals(currentPath)) {
                log.debug(STR."File \{currentPath} with Id \{procFile.getId()} does not need renaming.");
                return true;
            }

            if (Files.exists(targetFilePath)) {
                log.info(STR."Destination for File \{currentPath} with Id \{procFile.getId()} already exists: \{targetFilePath.toString()}");
                if (moveConfig.mode() != MoveConfig.Mode.NONE) {
                    val duplicateConfig = moveConfig.duplicates();
                    // Only handle duplicates if moving is enabled, else we want to rename in place so duplicate means it's name is correct
                    if (duplicateConfig.mode() == MoveConfig.HandlingConfig.Mode.DELETE) {
                        fileHandler.deleteFile(currentPath);
                    } else if (duplicateConfig.mode() == MoveConfig.HandlingConfig.Mode.MOVE) {
                        val subFolderWithFile = targetFilePath.subpath(targetFilePath.getNameCount() - 2, targetFilePath.getNameCount());
                        val targetPath = duplicateConfig.folder().resolve(subFolderWithFile);
                        if (fileHandler.renameFile(currentPath, targetPath)) {
                            procFile.setCurrentFile(targetPath);
                            if (renameConfig.related()) {
                                renameRelatedFiles(currentPath.getParent(), currentPath.getFileName().toString(), targetPath);
                            }
                        }
                    }
                }
                return false;
            }

            if (fileHandler.renameFile(currentPath, targetFilePath)) {
                log.debug(STR."File \{currentPath} with Id \{procFile.getId()} renamed to \{targetFilePath.toString()}");
                procFile.setCurrentFile(targetFilePath);
                if (renameConfig.related()) {
                    renameRelatedFiles(currentPath.getParent(), currentPath.getFileName().toString(), targetFilePath);
                }
                return true;
            }
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error(STR."Renaming failed for File \{procFile.getWorkingFile().getAbsolutePath()} with Id \{procFile.getId()}: \{ex.getMessage()}");
            return false;
        }
    }

    /**
     * Where the rename and move config would put this file, with the extension of the file as it is now.
     *
     * @param keepFolder ignore the move config and stay in the file's current folder
     * @return empty when the tag system or folder resolution failed
     */
    public Optional<Path> targetPath(FileInfo procFile, boolean keepFolder) throws Exception {
        val targetFolder = keepFolder
                ? Pair.<Path, TagSystemResult>of(procFile.getWorkingFile().getParentFile().toPath(), null)
                : getTargetFolder(procFile);
        if (targetFolder.getLeft() == null) {
            return Optional.empty();
        }
        val targetFileName = getTargetFileName(procFile, targetFolder.getRight());
        if (targetFileName.isEmpty()) {
            return Optional.empty();
        }
        val currentName = procFile.getWorkingFile().getName();
        val fileExtension = currentName.substring(currentName.lastIndexOf("."));
        var filename = stripExtension(targetFileName.get(), fileExtension) + fileExtension;
        filename = filename.replaceAll("[\\\\:\"/*|<>?]", "");

        val targetFolderPath = targetFolder.getLeft();
        if (filename.length() + targetFolderPath.toString().length() > 240) {
            filename = filename.substring(0, 240 - targetFolderPath.toString().length() - fileExtension.length()) + fileExtension;
        }
        return Optional.of(targetFolderPath.resolve(filename));
    }

    /**
     * rename.mode none yields the current file name including its extension; everything else yields a bare name.
     */
    private static String stripExtension(String name, String extension) {
        return name.endsWith(extension) ? name.substring(0, name.length() - extension.length()) : name;
    }

    /**
     * Moves the files that belong to a video along with it: everything in the old folder named like the old
     * video followed by "." or "-", which covers Kodi's episode .nfo and -thumb.jpg as well as subtitle
     * sidecars like .en.ass. Requiring the separator keeps a sibling whose name merely starts the same way,
     * such as "Episode 1" next to "Episode 10" or the renamed video itself, from being dragged along.
     */
    public void renameRelatedFiles(Path srcFolder, String oldFilename, Path newFile) {
        try {
            val oldFilenameWithoutExtension = oldFilename.substring(0, oldFilename.lastIndexOf("."));
            val newFilename = newFile.getFileName().toString();
            val newFilenameWithoutExtension = newFilename.substring(0, newFilename.lastIndexOf("."));
            if (oldFilenameWithoutExtension.equals(newFilenameWithoutExtension) && srcFolder.equals(newFile.getParent())) {
                return;
            }
            val srcFiles = srcFolder.toFile().listFiles((file) -> {
                val name = file.getName();
                if (!file.isFile() || name.equals(oldFilename) || name.equals(newFilename) || !name.startsWith(oldFilenameWithoutExtension)) {
                    return false;
                }
                val suffix = name.substring(oldFilenameWithoutExtension.length());
                return suffix.startsWith(".") || suffix.startsWith("-");
            });
            if (srcFiles == null) {
                return;
            }

            val relatedFileSuffixes = new HashSet<String>();
            for (File srcFile : srcFiles) {
                val relatedSuffix = srcFile.getName().substring(oldFilenameWithoutExtension.length());
                if (fileHandler.renameFile(srcFile.toPath(), newFile.getParent().resolve(newFilenameWithoutExtension + relatedSuffix))) {
                    relatedFileSuffixes.add(relatedSuffix);
                }
            }
            if (!relatedFileSuffixes.isEmpty()) {
                log.debug(STR."Renamed related files of \{oldFilename} to \{newFilenameWithoutExtension} with suffixes: \{String.join(", ", relatedFileSuffixes)}");
            }
        } catch (Exception e) {
            log.error(STR."Failed to rename related files of \{srcFolder.resolve(oldFilename)}: \{e.getMessage()}");
        }
    }

    private Optional<String> getTargetFileName(FileInfo procFile, TagSystemResult tagSystemResult) throws Exception {
        val renameConfig = procFile.config().rename();
        if (renameConfig.mode() == RenameConfig.Mode.NONE) {
            return Optional.of(procFile.getWorkingFile().getName());
        }
        if (renameConfig.mode() == RenameConfig.Mode.ANIDB) {
            return Optional.of(procFile.getData().get(TagSystemTags.FileAnidbFilename));
        }
        var tsResult = tagSystemResult == null ? getPathFromTagSystem(procFile) : tagSystemResult;
        if (tsResult == null) {
            log.error(STR."TagSystem script failed for File \{procFile.getWorkingFile().getAbsolutePath()} with Id \{procFile.getId()}. Check your tag system code.");
            return Optional.empty();
        }

        return Optional.of(tsResult.FileName());
    }

    private Pair<Path, TagSystemResult> getTargetFolder(FileInfo procFile) throws Exception {
        val moveConfig = procFile.config().move();
        if (moveConfig.mode() == MoveConfig.Mode.NONE) {
            return Pair.of(procFile.getWorkingFile().getParentFile().toPath(), null);
        }

        if (moveConfig.mode() == MoveConfig.Mode.FOLDER) {
            val moveToFolder = moveConfig.folder();
            return Pair.of(moveToFolder == null || moveToFolder.toString().isBlank() ? procFile.getWorkingFile().getParentFile().toPath() : moveToFolder, null);
        }

        val tagSystemResult = getPathFromTagSystem(procFile);
        if (tagSystemResult == null) {
            log.error(STR."TagSystem script failed for File \{procFile.getWorkingFile().getAbsolutePath()} with Id \{procFile.getId()}. Check your tag system code.");
            return Pair.of(null, null);
        }

        val pathName = tagSystemResult.PathName();
        if (pathName == null) {
            return Pair.of(procFile.getWorkingFile().getParentFile().toPath(), tagSystemResult);
        }

        if (pathName.length() > 240) {
            throw new Exception("Pathname too long. Check your tag system code or your base folders.");
        }

        val targetFolder = Path.of(pathName);
        if (!targetFolder.isAbsolute()) {
            log.warn(STR."Folderpath for moving from TagSystem needs to be absolute but is \{targetFolder.toString()}");
            return Pair.of(null, tagSystemResult);
        }

        return Pair.of(targetFolder, tagSystemResult);
    }

    private TagSystemResult getPathFromTagSystem(FileInfo procFile) throws Exception {
        val tags = new HashMap<>(procFile.getData());
        tags.put(TagSystemTags.FileCurrentFilename, procFile.getWorkingFile().getName());

        String codeStr = tagsConfig.tagSystem();
        if (codeStr == null || codeStr.isEmpty()) {
            return null;
        }

        return TagSystem.Evaluate(codeStr, tags, tagsConfig.paths());
    }
}

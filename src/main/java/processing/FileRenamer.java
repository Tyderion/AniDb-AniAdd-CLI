package processing;

import config.CliConfiguration;
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
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class FileRenamer {

    private final IFileHandler fileHandler;

    public boolean renameFile(FileInfo procFile) {
        val configuration = procFile.getConfiguration();
        val moveConfig = configuration.getMove();
        val renameConfig = configuration.getRename();
        try {

            val targetFolder = getTargetFolder(procFile);
            val targetFileName = getTargetFileName(procFile, targetFolder.getRight());

            if (targetFileName.isEmpty()) {
                return false;
            }

            val fileExtension = procFile.getFile().getName().substring(procFile.getFile().getName().lastIndexOf("."));
            var filename = targetFileName.get() + fileExtension;
            filename = filename.replaceAll("[\\\\:\"/*|<>?]", "");

            val targetFolderPath = targetFolder.getLeft();

            if (targetFileName.get().length() + targetFolderPath.toString().length() > 240) {
                filename = filename.substring(0, 240 - targetFolderPath.toString().length() - fileExtension.length()) + fileExtension;
            }
            val targetFilePath = targetFolderPath.resolve(filename);

            if (Files.exists(targetFilePath)) {
                log.info(STR."Destination for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} already exists: \{targetFilePath.toString()}");
                if (moveConfig.getMode() != CliConfiguration.MoveConfig.Mode.NONE) {
                    val duplicateConfig = moveConfig.getDuplicates();
                    // Only handle duplicates if moving is enabled, else we want to rename in place so duplicate means it's name is correct
                    if (duplicateConfig.getMode() == CliConfiguration.MoveConfig.HandlingConfig.Mode.DELETE) {
                        fileHandler.deleteFile(procFile.getFile().toPath());
                    } else if (duplicateConfig.getMode() == CliConfiguration.MoveConfig.HandlingConfig.Mode.MOVE) {
                        val oldFilename = procFile.getFile().getName();
                        val subFolderWithFile = targetFilePath.subpath(targetFilePath.getNameCount() - 2, targetFilePath.getNameCount());
                        val targetPath = Paths.get(duplicateConfig.getFolder()).resolve(subFolderWithFile);
                        fileHandler.renameFile(procFile.getFile().toPath(), targetPath);
                        if (renameConfig.isRelated()) {
                            renameRelatedFiles(procFile, oldFilename, targetPath.getFileName().toString(), targetPath.getParent());
                        }
                    }
                }
                return false;
            }
            if (targetFilePath.equals(procFile.getFile().toPath().toAbsolutePath())) {
                log.debug(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} does not need renaming.");
                return true;
            }

            val oldFilename = procFile.getFile().getName();
            if (fileHandler.renameFile(procFile.getFile().toPath(), targetFilePath)) {
                log.debug(STR."File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()} renamed to \{targetFilePath.toString()}");
                if (renameConfig.isRelated()) {
                    renameRelatedFiles(procFile, oldFilename, targetFilePath.getFileName().toString(), targetFolderPath);
                }

                procFile.setRenamedFile(targetFilePath);
                return true;
            }
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error(STR."Renaming failed for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}: \{ex.getMessage()}");
            return false;
        }
    }

    private void renameRelatedFiles(FileInfo procFile, String oldFilename, String newFilename, Path folderPath) {
        try {
            val srcFolder = procFile.getFile().getParentFile();
            val oldFilenameWithoutExtension = oldFilename.substring(0, oldFilename.lastIndexOf("."));
            val srcFiles = srcFolder.listFiles((file) -> file.getName().startsWith(oldFilenameWithoutExtension) && !file.getName().equals(oldFilename));

            val relatedFileSuffixes = new HashSet<String>();

            val newFilenameWithoutExtension = newFilename.substring(0, newFilename.lastIndexOf("."));
            for (File srcFile : srcFiles) {
                val relatedSuffix = srcFile.getName().substring(oldFilenameWithoutExtension.length());
                if (fileHandler.renameFile(srcFile.toPath(), folderPath.resolve(newFilenameWithoutExtension + relatedSuffix))) {
                    relatedFileSuffixes.add(relatedSuffix);
                }
            }
            if (!relatedFileSuffixes.isEmpty()) {
                log.debug(STR."Renamed related files for \{procFile.getFile().getAbsolutePath()} with suffixes: \{String.join(", ", relatedFileSuffixes)}");
            }
        } catch (Exception e) {
            log.error(STR."Failed to rename related files for \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}: \{e.getMessage()}");
        }
    }

    private static Optional<String> getTargetFileName(FileInfo procFile, TagSystemResult tagSystemResult) throws Exception {
        val renameConfig = procFile.getConfiguration().getRename();
        if (renameConfig.getMode() == CliConfiguration.RenameConfig.Mode.NONE) {
            return Optional.of(procFile.getFile().getName());
        }
        if (renameConfig.getMode() == CliConfiguration.RenameConfig.Mode.ANIDB) {
            return Optional.of(procFile.getData().get(TagSystemTags.FileAnidbFilename));
        }
        var tsResult = tagSystemResult == null ? getPathFromTagSystem(procFile) : tagSystemResult;
        if (tsResult == null) {
            log.error(STR."TagSystem script failed for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}. Check your tag system code.");
            return Optional.empty();
        }

        return Optional.of(tsResult.FileName());
    }

    private static Pair<Path, TagSystemResult> getTargetFolder(FileInfo procFile) throws Exception {
        val moveConfig = procFile.getConfiguration().getMove();
        if (moveConfig.getMode() == CliConfiguration.MoveConfig.Mode.NONE) {
            return Pair.of(procFile.getFile().getParentFile().toPath(), null);
        }

        if (moveConfig.getMode() == CliConfiguration.MoveConfig.Mode.FOLDER) {
            val moveToFolder = moveConfig.getFolder();
            return Pair.of(moveToFolder.isEmpty() ? procFile.getFile().getParentFile().toPath() : Paths.get(moveToFolder), null);
        }

        val tagSystemResult = getPathFromTagSystem(procFile);
        if (tagSystemResult == null) {
            log.error(STR."TagSystem script failed for File \{procFile.getFile().getAbsolutePath()} with Id \{procFile.getId()}. Check your tag system code.");
            return Pair.of(null, null);
        }

        val pathName = tagSystemResult.PathName();
        if (pathName == null) {
            return Pair.of(procFile.getFile().getParentFile().toPath(), tagSystemResult);
        }

        if (pathName.length() > 240) {
            throw new Exception("Pathname too long. Check your tag system code or your base folders.");
        }

        val targetFolder = Paths.get(pathName);
        if (!targetFolder.isAbsolute()) {
            log.warn(STR."Folderpath for moving from TagSystem needs to be absolute but is \{targetFolder.toString()}");
            return Pair.of(null, tagSystemResult);
        }

        return Pair.of(targetFolder, tagSystemResult);
    }

    private static TagSystemResult getPathFromTagSystem(FileInfo procFile) throws Exception {
        val tags = new HashMap<>(procFile.getData());
        tags.put(TagSystemTags.FileCurrentFilename, procFile.getFile().getName());
        val configuration = procFile.getConfiguration();

        String codeStr = configuration.getTagSystem();
        if (codeStr == null || codeStr.isEmpty()) {
            return null;
        }

        return TagSystem.Evaluate(codeStr, tags, configuration.getPaths());
    }
}

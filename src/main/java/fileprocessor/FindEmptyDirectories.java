package fileprocessor;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.java.Log;
import lombok.val;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Log
@RequiredArgsConstructor
public class FindEmptyDirectories implements Callable<List<Path>> {

    private final String directory;

    @Override
    public List<Path> call() throws Exception {
        val result = getEmptyDirectories(Paths.get(directory));
        val files = result.getDeletableChildren();
        files.forEach(f -> log.info(STR."Found empty directory: \{f.toAbsolutePath().toString()}"));
        return files;
    }

    private FindResult getEmptyDirectories(Path root) {
        try (val files = Files.find(root, 1, (path, attr) -> !path.toString().equals(root.toString()))) {
            val childFiles = files.toList();
            // if the directory is empty, it can be deleted
            if (childFiles.isEmpty()) {
                return new FindResult(0, List.of(root));
            }
            val emptyChildDirectories = childFiles.stream().filter(Files::isDirectory).map(this::getEmptyDirectories).toList();
            val fileCount = childFiles.stream().filter(Files::isRegularFile).count();
            val subResult = emptyChildDirectories.stream().reduce(new FindResult((int) fileCount, List.of()),
                    // Add up all children and their children
                    (a, b) -> new FindResult(a.totalChildrenCount + b.totalChildrenCount + 1,
                            Stream.concat(a.deletableChildren.stream(), b.deletableChildren.stream()).toList()));

            // if all children can be deleted, the parent can be deleted as well
            if (subResult.totalChildrenCount == subResult.getDeletableChildren().size()) {
                return new FindResult(subResult.totalChildrenCount,
                        Stream.concat(subResult.getDeletableChildren().stream(), Stream.of(root)).toList());
            } else {
                return subResult;
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Value
    private static class FindResult {
        int totalChildrenCount;
        List<Path> deletableChildren;
    }

    private static boolean isDirEmpty(final Path directory) {
        try (val dirStream = Files.newDirectoryStream(directory)) {
            return !dirStream.iterator().hasNext();
        } catch (IOException e) {
            log.severe(STR."Could not check if directory is empty: \{e.getMessage()}");
            return false;
        }
    }

    private boolean shouldScrapeFile(File file) {
        return !file.isDirectory() && !isKodiMetadataFileOrInvalidFile(file.getName());
    }

    private boolean isKodiMetadataFileOrInvalidFile(String name) {
        return name.endsWith(".jpg")
                || name.endsWith(".nfo")
                || name.endsWith(".srt")
                || name.endsWith(".sub")
                || name.endsWith(".png")
                || name.equalsIgnoreCase("thumbs.db");
    }

}

package fileprocessor;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.java.Log;
import lombok.val;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Log
@RequiredArgsConstructor
public class FindEmptyDirectories implements Callable<List<Path>> {

    private final String directory;

    @Override
    public List<Path> call() throws Exception {
        val result = getEmptyDirectories(Paths.get(directory));
//
//        Iterable<Path> paths = () -> recursivelyGetEmptyDirs(Paths.get(directory)).collect(Collectors.toCollection(ArrayDeque::new))
//                .descendingIterator();
//        val files = StreamSupport.stream(paths.spliterator(), false).toList();
        val files = result.getEmptyChildren();
        files.forEach(f -> log.info(STR."Found empty directory: \{f.toAbsolutePath().toString()}"));
        log.info(STR."Number of found files: \{files.size()}");
        return files;
    }

    private FindResult getEmptyDirectories(Path root) {
        try (val files = Files.find(root, 1, (path, attr) -> !path.toString().equals(root.toString()))) {
            val childFiles = files.toList();
            if (childFiles.isEmpty()) {
                return new FindResult(0, List.of(root));
            }
            val emptyChildDirectories = childFiles.stream().filter(Files::isDirectory).map(this::getEmptyDirectories).toList();
            val fileCount = childFiles.stream().filter(Files::isRegularFile).count();
            val subResult = emptyChildDirectories.stream().reduce(new FindResult((int) fileCount, List.of()),
                    (a, b) -> new FindResult(a.childrenCount + b.childrenCount + 1,
                            Stream.concat(a.emptyChildren.stream(), b.emptyChildren.stream()).toList()));
            if (subResult.childrenCount == subResult.getEmptyChildren().size()) {
                return new FindResult(subResult.childrenCount + (int) fileCount,
                        Stream.concat(subResult.getEmptyChildren().stream(), Stream.of(root)).toList());
            } else {
                return subResult;
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Value
    private class FindResult {
        int childrenCount;
        List<Path> emptyChildren;
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

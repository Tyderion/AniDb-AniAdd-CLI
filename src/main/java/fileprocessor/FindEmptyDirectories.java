package fileprocessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log
@RequiredArgsConstructor
public class FindEmptyDirectories implements Callable<List<Path>> {

    private final String directory;

    @Override
    public List<Path> call() throws Exception {
        val files = recursivelyGetEmptyDirs(Paths.get(directory)).collect(Collectors.toList());
        files.forEach(f -> log.finest(STR."Found empty directory: \{f.toAbsolutePath().toString()}"));
        log.info(STR."Number of found files: \{files.size()}");
        return files;
    }

    private Stream<Path> recursivelyGetEmptyDirs(Path folder) {
        if (!Files.exists(folder) || !Files.isDirectory(folder)) {
            return Stream.empty();
        }
        try (val emptySubfolders = Files.find(folder, 1, (path, attr) -> attr.isDirectory() && isDirEmpty(path))) {
            try (val otherSubfolders = Files.find(folder, 1, (path, attr) -> attr.isDirectory())) {
                return otherSubfolders.reduce(emptySubfolders, (acc, ele) -> Stream.concat(acc, recursivelyGetEmptyDirectoriesToScrape(ele)), Stream::concat);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

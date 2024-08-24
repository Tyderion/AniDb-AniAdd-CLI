package fileprocessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class FindFiles implements Callable<List<File>> {

    private final Path directory;

    @Override
    public List<File> call() throws Exception {
        log.debug(STR."Folder: \{directory.toAbsolutePath()}");

        val files = recursivelyGetFilesToScrape(directory.toFile()).collect(Collectors.toList());
        files.forEach(f -> log.trace(STR."Found file: \{f.getAbsolutePath()}"));
        log.info(STR."Number of found files: \{files.size()}");
        return files;
    }

    private Stream<File> recursivelyGetFilesToScrape(File folder) {
        if (!folder.exists() || !folder.isDirectory()) {
            return Arrays.stream(new File[0]);
        }
        val files = Arrays.stream(folder.listFiles(this::shouldScrapeFile));
        val subFolderFiles = Arrays.stream(folder.listFiles(File::isDirectory)).flatMap(this::recursivelyGetFilesToScrape);
        return Stream.concat(files, subFolderFiles);
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

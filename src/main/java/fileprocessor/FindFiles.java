package fileprocessor;

import lombok.RequiredArgsConstructor;
import lombok.val;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class FindFiles implements Callable<List<File>> {

    private final String directory;
    private final Logger logger = Logger.getLogger(FindFiles.class.getName());

    @Override
    public List<File> call() throws Exception {
        File folder = new File(directory);

        logger.info(STR."Folder: \{folder.getAbsolutePath()}");

        val files = recursivelyGetFilesToScrape(folder).collect(Collectors.toList());
        files.forEach(f -> logger.info(STR."Found file: \{f.getAbsolutePath()}"));
        logger.info(STR."Number of found files: \{files.size()}");
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

    private boolean shouldScrapeFile(File _directory, String name) {
        return !isKodiMetadataFileOrInvalidFile(_directory, name);
    }

    private boolean isKodiMetadataFileOrInvalidFile(File _directory, String name) {
        return name.endsWith(".jpg")
                || name.endsWith(".nfo")
                || name.endsWith(".srt")
                || name.endsWith(".sub")
                || name.endsWith(".png")
                || name.equalsIgnoreCase("thumbs.db");
    }

}

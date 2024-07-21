package fileprocessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.stream.Collectors;

@Log
@RequiredArgsConstructor
public class DeleteEmptyChildDirectoriesRecursively implements Runnable{
    private final Path root;
    @Override
    public void run() {
        try (val files = Files.walk(root, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS)) {
            files.collect(Collectors.toCollection(ArrayDeque::new)).descendingIterator().forEachRemaining(path -> {
                if (Files.isDirectory(path) && !path.equals(root)) {
                    try(val children = Files.list(path)) {
                        if (children.findAny().isEmpty()) {
                            Files.delete(path);
                            log.info(STR."Deleted empty directory: \{path}");
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

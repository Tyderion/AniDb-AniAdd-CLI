package processing;

import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Log
public class FileHandler implements IFileHandler {

    @Override
    public boolean renameFile(@NotNull Path from, @NotNull Path to) {
        if (from.toAbsolutePath().equals(to.toAbsolutePath())) {
            log.info(STR."File \{from.toAbsolutePath()} is already at the correct location.");
            return true;
        }
        log.fine(STR."Moving file from \{from.toAbsolutePath()} to \{to.toAbsolutePath()}");
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            log.info(STR."Could not move file from \{from.toAbsolutePath()} to \{to.toAbsolutePath()}: \{e.getMessage()}. Will try to copy instead");
        }
        log.fine(STR."Copying file from \{from.toAbsolutePath()} to \{to.toAbsolutePath()}");
        try {
            Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
            Files.delete(from);
            return true;
        } catch (IOException e) {
            log.severe(STR."Could not move file from \{from.toAbsolutePath()} to \{to.toAbsolutePath()}: \{e.getMessage()}.");
        }
        return false;
    }

    @Override
    public void deleteFile(@NotNull Path path) {
        log.fine(STR."Deleting file \{path.toAbsolutePath()}");
        try {
            Files.delete(path);
        } catch (IOException e) {
            log.severe(STR."Could not delete file \{path.toAbsolutePath()}: \{e.getMessage()}");
        }
    }
}

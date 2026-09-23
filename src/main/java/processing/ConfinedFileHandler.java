package processing;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Refuses any move, rename or delete that would touch a path outside a folder. Every file operation the
 * processing pipeline performs goes through an IFileHandler, so this is the one place that sees where files
 * really go, including destinations a tag system computes at run time.
 *
 * A refused move reports failure rather than throwing: the file stays where it is, the pipeline records no
 * new location, and metadata, which follows the file's actual location, is written beside it too.
 */
@Slf4j
public class ConfinedFileHandler implements IFileHandler {
    private final IFileHandler delegate;
    private final Path root;

    public ConfinedFileHandler(IFileHandler delegate, Path root) {
        this.delegate = delegate;
        this.root = root;
    }

    @Override
    public boolean renameFile(@NotNull Path from, @NotNull Path to) {
        if (!Confinement.contains(root, from) || !Confinement.contains(root, to)) {
            log.error("Refusing to move " + from + " to " + to + ": file.move.confineTo allows only " + root);
            return false;
        }
        return delegate.renameFile(from, to);
    }

    @Override
    public void deleteFile(@NotNull Path path) {
        if (!Confinement.contains(root, path)) {
            log.error("Refusing to delete " + path + ": file.move.confineTo allows only " + root);
            return;
        }
        delegate.deleteFile(path);
    }
}

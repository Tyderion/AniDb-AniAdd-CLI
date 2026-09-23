package processing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;

/**
 * Whether a path is inside a folder, judged by where it really is on disk rather than how it is spelled.
 * A lexical check would pass a path that runs through a symlink out of the folder, and the destinations
 * checked here usually do not exist yet, so the nearest existing ancestor is resolved and the rest appended.
 */
public final class Confinement {
    private Confinement() {
    }

    public static boolean contains(Path root, Path candidate) {
        return real(candidate).startsWith(real(root));
    }

    static Path real(Path path) {
        Path existing = path.toAbsolutePath().normalize();
        ArrayDeque<Path> missing = new ArrayDeque<>();
        while (existing != null && !Files.exists(existing)) {
            missing.push(existing.getFileName());
            existing = existing.getParent();
        }
        Path resolved;
        try {
            resolved = existing == null ? Path.of("/") : existing.toRealPath();
        } catch (IOException e) {
            resolved = existing;
        }
        while (!missing.isEmpty()) {
            resolved = resolved.resolve(missing.pop());
        }
        return resolved;
    }
}

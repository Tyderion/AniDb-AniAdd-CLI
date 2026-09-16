package kodi.library;

import config.blocks.KodiLibraryScanConfig.Scope;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Turns the files a scan run touched into the list of Kodi directories to scan. Pure: no Kodi, no filesystem.
 */
@Slf4j
public final class LibraryScanPlanner {

    /**
     * @param kodiPath  the library root as Kodi sees it
     * @param localPath the same root as this process sees it, or null when unknown
     */
    public record ResolvedLibrary(String kodiPath, Path localPath) {
        public ResolvedLibrary {
            kodiPath = withTrailingSeparator(kodiPath);
            localPath = localPath == null ? null : localPath.toAbsolutePath().normalize();
        }
    }

    private LibraryScanPlanner() {
    }

    /**
     * With {@link Scope#CHANGED}, a changed file maps to the top-level folder below its library root (the show or movie
     * folder, even when the file sits in a season subfolder), or to the root itself for files placed directly in it.
     * Libraries without a local path cannot be mapped and are scanned whole. Directories already covered by a root
     * scan are dropped.
     */
    public static List<String> plan(Scope scope, List<ResolvedLibrary> libraries, Collection<Path> changedFiles) {
        if (changedFiles.isEmpty()) {
            return List.of();
        }
        val directories = new LinkedHashSet<String>();
        if (scope == Scope.LIBRARY) {
            libraries.forEach(library -> directories.add(library.kodiPath()));
            return List.copyOf(directories);
        }

        libraries.stream().filter(library -> library.localPath() == null).forEach(library -> directories.add(library.kodiPath()));
        for (val changedFile : changedFiles) {
            val file = changedFile.toAbsolutePath().normalize();
            findLibrary(libraries, file).ifPresentOrElse(
                    library -> directories.add(kodiDirectoryFor(library, file)),
                    () -> log.debug(STR."\{file} lies in no configured kodi library, not scanning it"));
        }
        return collapse(directories);
    }

    private static Optional<ResolvedLibrary> findLibrary(List<ResolvedLibrary> libraries, Path file) {
        return libraries.stream()
                .filter(library -> library.localPath() != null && file.startsWith(library.localPath()) && !file.equals(library.localPath()))
                .max(Comparator.comparingInt(library -> library.localPath().getNameCount()));
    }

    private static String kodiDirectoryFor(ResolvedLibrary library, Path file) {
        val relative = library.localPath().relativize(file);
        if (relative.getNameCount() <= 1) {
            return library.kodiPath();
        }
        return withTrailingSeparator(library.kodiPath() + relative.getName(0));
    }

    private static List<String> collapse(Collection<String> directories) {
        val result = new ArrayList<String>();
        for (val directory : directories) {
            val covered = directories.stream().anyMatch(other -> !other.equals(directory) && directory.startsWith(other));
            if (!covered) {
                result.add(directory);
            }
        }
        return result;
    }

    /** Kodi paths may be posix, smb:// or Windows paths, so the separator is taken from the path itself. */
    static String withTrailingSeparator(String kodiPath) {
        val separator = kodiPath.contains("/") || !kodiPath.contains("\\") ? "/" : "\\";
        return kodiPath.endsWith(separator) ? kodiPath : kodiPath + separator;
    }
}

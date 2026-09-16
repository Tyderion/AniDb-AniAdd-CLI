package config.blocks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;
import lombok.experimental.Accessors;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks Kodi to scan its video library after a batch of files was moved, renamed or got new metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class KodiLibraryScanConfig {
    private static final int DEFAULT_TIMEOUT_IN_MINUTES = 30;

    @Builder.Default
    private boolean enabled = false;

    @Builder.Default
    private Scope scope = Scope.CHANGED;

    /** Show Kodi's scan progress bar on screen */
    @Builder.Default
    private boolean showDialogs = false;

    /** How long to wait for one scan to finish before starting the next one anyway */
    @Builder.Default
    private int timeoutInMinutes = DEFAULT_TIMEOUT_IN_MINUTES;

    @Builder.Default
    private List<Library> libraries = new ArrayList<>();

    public enum Scope {
        /** Scan only the top-level folders (a show, a movie) below a library that received changes. Needs {@link Library#localPath}. */
        CHANGED,
        /** Scan every configured library whenever anything changed */
        LIBRARY
    }

    /**
     * One Kodi video source, identified either by its label as shown in Kodi ({@code name}) or by its path as Kodi sees it ({@code path}).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class Library {
        /** Source label in Kodi, matched case-insensitively and resolved via Files.GetSources */
        private String name;
        /** Source path as Kodi sees it, e.g. /storage/media/anime/series/ or smb://nas/anime/ */
        private String path;
        /** The same folder as this process sees it. Lets changed files be mapped to Kodi paths. */
        private Path localPath;

        public String describe() {
            return name != null ? STR."'\{name}'" : path;
        }
    }

    /**
     * @return one message per configuration mistake, empty when the block is usable
     */
    public List<String> problems() {
        val problems = new ArrayList<String>();
        if (!enabled) {
            return problems;
        }
        if (libraries == null || libraries.isEmpty()) {
            problems.add("kodi.libraryScan is enabled but lists no libraries");
            return problems;
        }
        if (timeoutInMinutes < 1) {
            problems.add("kodi.libraryScan.timeoutInMinutes must be at least 1");
        }
        for (int i = 0; i < libraries.size(); i++) {
            val library = libraries.get(i);
            val hasName = library.name() != null && !library.name().isBlank();
            val hasPath = library.path() != null && !library.path().isBlank();
            if (hasName == hasPath) {
                problems.add(STR."kodi.libraryScan.libraries[\{i}] needs exactly one of 'name' or 'path'");
            }
        }
        return problems;
    }

    public boolean isDefault() {
        return !enabled && scope == Scope.CHANGED && !showDialogs && timeoutInMinutes == DEFAULT_TIMEOUT_IN_MINUTES
                && (libraries == null || libraries.isEmpty());
    }
}

package config.blocks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class TagsConfig {
    private PathConfig paths;
    private String tagSystem;

    /**
     * A tag system kept in its own file instead of inline. Relative paths resolve against the config file,
     * so several configs can share one definition rather than each carrying a copy that drifts.
     */
    private Path tagSystemFile;

    /** Not part of the config: holds the file contents so a rename does not re-read it per file. */
    private transient String loadedTagSystem;

    public String tagSystem() {
        if (tagSystem != null && !tagSystem.isBlank()) {
            if (tagSystemFile != null) {
                log.warn(STR."Both tagSystem and tagSystemFile are set; using the inline one and ignoring \{tagSystemFile}");
            }
            return tagSystem;
        }
        if (tagSystemFile == null) {
            return tagSystem;
        }
        if (loadedTagSystem == null) {
            try {
                loadedTagSystem = Files.readString(tagSystemFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error(STR."Could not read tag system from \{tagSystemFile}: \{e.getMessage()}");
                return null;
            }
        }
        return loadedTagSystem;
    }
}

package config.blocks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.nio.file.Path;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class MoveConfig {
    @Builder.Default
    Mode mode = Mode.NONE;
    private Path folder;
    private boolean deleteEmptyDirs;

    @Builder.Default
    private HandlingConfig duplicates = HandlingConfig.builder().build();
    @Builder.Default
    private HandlingConfig unknown = HandlingConfig.builder().build();

    public void removeDefaults() {
        this.duplicates.removeDefaults();
        if (this.duplicates.isDefault()) {
            this.duplicates = null;
        }
        this.unknown.removeDefaults();
        if (this.unknown.isDefault()) {
            this.unknown = null;
        }
        if (this.folder != null && this.folder.toString().isBlank()) {
            this.folder = null;
        }
    }

    public enum Mode {
        TAGSYSTEM,
        FOLDER,
        NONE
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HandlingConfig {
        @Builder.Default
        private HandlingConfig.Mode mode = HandlingConfig.Mode.NONE;
        private Path folder;

        public enum Mode {
            MOVE, DELETE, NONE
        }

        public boolean isDefault() {
            return mode == HandlingConfig.Mode.NONE && folder == null;
        }

        public void removeDefaults() {
            if (folder != null && folder.toString().isBlank()) {
                folder = null;
            }
        }
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FolderConfig {
        private Path tvshows;
        private Path movies;
    }
}

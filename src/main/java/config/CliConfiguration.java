package config;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Slf4j
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CliConfiguration {

    @Builder.Default
    private MyListConfig mylist = MyListConfig.builder().build();
    @Builder.Default
    private RenameConfig rename = RenameConfig.builder().build();
    @Builder.Default
    private AniDbConfig anidb = AniDbConfig.builder().build();
    @Builder.Default
    private MoveConfig move = MoveConfig.builder().build();
    private PathConfig paths;
    private RunConfig run;
    private String tagSystem;

    public void removeDefaults() {
        move.removeDefaults();
        anidb.removeDefaults();
    }


    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathConfig {
        @Singular
        private List<Single> tvShowFolders;
        @Singular
        private List<Single> movieFolders;

        @Data
        @Builder(toBuilder = true)
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Single {
            private String path;
            private String tagSystemName;
        }

        public Optional<Path> getEpisodePath(Path relativePath) {
            return tvShowFolders.stream()
                    .map(folder -> Path.of(folder.getPath()).resolve(relativePath))
                    .filter(Files::exists)
                    .findFirst();
        }

        public Optional<Path> getMoviePath(Path relativePath) {
            return movieFolders.stream()
                    .map(folder -> Path.of(folder.getPath()).resolve(relativePath))
                    .filter(Files::exists)
                    .findFirst();
        }
    }


    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoveConfig {
        @Builder.Default
        Mode mode = MoveConfig.Mode.NONE;
        private String folder;
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
            if (this.folder != null && this.folder.isBlank()) {
                this.folder = null;
            }
        }

        public enum Mode {
            TAGSYSTEM,
            FOLDER,
            NONE
        }

        @Data
        @Builder(toBuilder = true)
        @NoArgsConstructor
        @AllArgsConstructor
        public static class HandlingConfig {
            @Builder.Default
            private Mode mode = Mode.IGNORE;
            private String folder;

            public enum Mode {
                MOVE, DELETE, IGNORE
            }

            public boolean isDefault() {
                return mode == null && folder == null;
            }

            public void removeDefaults() {
                if (mode == Mode.IGNORE) {
                    mode = null;
                }
                if (folder != null && folder.isBlank()) {
                    folder = null;
                }
            }
        }

        @Data
        @Builder(toBuilder = true)
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FolderConfig {
            private String tvshows;
            private String movies;
        }
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RenameConfig {
        @Builder.Default
        Mode mode = Mode.NONE;
        private boolean related;

        public enum Mode {
            TAGSYSTEM,
            ANIDB,
            NONE
        }
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MyListConfig {
        private boolean overwrite;
        private boolean add;
        @Builder.Default
        StorageType storageType = StorageType.UNKNOWN;
        private boolean watched;

        @Getter
        @RequiredArgsConstructor
        public enum StorageType {
            UNKNOWN(0), INTERNAL(1), EXTERNAL(2), DELETED(3), REMOTE(4);
            private final int value;
        }
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AniDbConfig {
        @Builder.Default
        private String host = "api.anidb.net";
        @Builder.Default
        private Integer port = 9000;

        @Builder.Default
        private CacheConfig cache = CacheConfig.builder().build();

        @Data
        @Builder(toBuilder = true)
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CacheConfig {
            @Builder.Default
            private int ttlInDays = 30;
            @Builder.Default
            private String fileName = "aniAdd.sqlite";
        }

        public void removeDefaults() {
            if (host.equals("api.anidb.net")) {
                host = null;
            }
            if (port == 9000) {
                port = null;
            }
        }
    }
}

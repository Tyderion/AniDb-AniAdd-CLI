package config;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Slf4j

    @Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class CliConfiguration {

    @Builder.Default
    private MyListConfig mylist = MyListConfig.builder().build();
    @Builder.Default
    private FileConfig file = FileConfig.builder().build();
    @Builder.Default
    private AniDbConfig anidb = AniDbConfig.builder().build();
    private RunConfig run;
    @Builder.Default
    private TagsConfig tags = TagsConfig.builder().build();

    @Builder.Default
    private KodiConfig kodi = KodiConfig.builder().build();

    public void removeDefaults() {
        file.removeDefaults();
        anidb.removeDefaults();
        this.kodi.removeDefaults();
        if (this.kodi.isEmpty()) {
            this.kodi = null;
        }

        this.anidb().exitOnBan(false);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagsConfig {
        private PathConfig paths;
        private String tagSystem;
    }



    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathConfig {
        @Singular
        private List<Single> tvShowFolders;
        @Singular
        private List<Single> movieFolders;


    @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Single {
            private Path path;
            private String tagSystemName;
        }

        public Optional<Path> getEpisodePath(Path relativePath) {
            return tvShowFolders.stream()
                    .map(folder -> folder.path().resolve(relativePath))
                    .filter(Files::exists)
                    .findFirst();
        }

        public Optional<Path> getMoviePath(Path relativePath) {
            return movieFolders.stream()
                    .map(folder -> folder.path().resolve(relativePath))
                    .filter(Files::exists)
                    .findFirst();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileConfig {
        @Builder.Default
        private RenameConfig rename = RenameConfig.builder().build();
        @Builder.Default
        private MoveConfig move = MoveConfig.builder().build();

        public void removeDefaults() {
            this.move.removeDefaults();
        }
    }

    @Data
    @Builder
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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoveConfig {
        @Builder.Default
        Mode mode = MoveConfig.Mode.NONE;
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
            private Mode mode = Mode.IGNORE;
            private Path folder;

            public enum Mode {
                MOVE, DELETE, IGNORE
            }

            public boolean isDefault() {
                return mode == Mode.IGNORE && folder == null;
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




    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KodiConfig {
        @Builder.Default
        private String host = "localhost";
        @Builder.Default
        private Integer port = 9090;
        @Builder.Default
        private String pathFilter = "anime";

        public boolean isEmpty() {
            return host == null && port == null;
        }

        public void removeDefaults() {
            if (host.equals("localhost")) {
                host = null;
            }
            if (port == 9000) {
                port = null;
            }
        }
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MyListConfig {
        public String username;
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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AniDbConfig {
        @Builder.Default
        private String host = "api.anidb.net";
        @Builder.Default
        private Integer port = 9000;
        private String username;
        @Builder.Default
        private Integer localPort =  3333;

        @Builder.Default
        private Boolean exitOnBan = false;
        /**
         * Rejected if set in the config file
         */
        private String password;

        @Builder.Default
        private CacheConfig cache = CacheConfig.builder().build();


    @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CacheConfig {
            @Builder.Default
            private int ttlInDays = 30;
            @Builder.Default
            private Path db = Path.of("aniAdd.sqlite");
        }

        public void removeDefaults() {
            this.password = null;
            if (host.equals("api.anidb.net")) {
                host = null;
            }
            if (port == 9000) {
                port = null;
            }
        }
    }
}

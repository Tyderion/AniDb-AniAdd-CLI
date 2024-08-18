package config;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

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
    private PathConfig paths = PathConfig.builder().build();
    private String tagSystem;


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
    }


    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoveConfig {
        @Builder.Default
        Type type = Type.NONE;
        private String folder;
        private boolean deleteEmptyDirs;

        @Builder.Default
        private HandlingConfig duplicates = HandlingConfig.builder().build();
        @Builder.Default
        private HandlingConfig unknown = HandlingConfig.builder().build();

        public enum Type {
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
            private Type type = Type.IGNORE;
            private String folder;

            public enum Type {
                MOVE, DELETE, IGNORE
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
        Type type = Type.NONE;
        private boolean related;

        public enum Type {
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
        private int port = 9000;

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
    }
}

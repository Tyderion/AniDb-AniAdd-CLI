package aniAdd.config;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Paths;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AniConfiguration {

    private boolean enableFileMove;
    private boolean enableFileRenaming;
    private boolean overwriteMLEntries;
    private boolean recursivelyDeleteEmptyFolders;
    @Builder.Default
    private boolean renameFiles = false;
    private boolean renameRelatedFiles;
    private boolean renameTypeAniDBFileName;
    @Builder.Default
    private StorageType setStorageType = StorageType.UNKOWN;
    private boolean setWatched;
    @Builder.Default
    private boolean addToMylist = false;
    private boolean advancedMode;
    private boolean moveTypeUseFolder;
    private String tagSystemCode;
    private boolean deleteDuplicateFiles;
    private String tvShowFolder;
    private String movieFolder;
    @Builder.Default
    private int anidbPort = 9000;
    @Builder.Default
    private String anidbHost = "api.anidb.net";

    public String getEpisodePath(String relativePath) {
        return Paths.get(tvShowFolder, relativePath).toString();
    }

    public String getMoviePath(String relativePath) {
        return Paths.get(movieFolder, relativePath).toString();
    }

    public enum StorageType {
        UNKOWN(0), INTERNAL(1), EXTERNAL(2), DELETE(3), REMOTE(4);

        private final int value;

        private StorageType(int i) {
            value = i;
        }

        public int getValue() {
            return value;
        }
    }
    
}

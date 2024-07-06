package aniAdd.config;


import lombok.Data;

@Data
public class AniConfiguration {

    private boolean enableFileMove;
    private boolean enableFileRenaming;
    private String moveToFolder;
    private boolean overwriteMLEntries;
    private boolean recursivelyDeleteEmptyFolders;
    private boolean renameFiles = false;
    private boolean renameRelatedFiles;
    private boolean renameTypeAniDBFileName;
    private StorageType setStorageType = StorageType.UNKOWN;
    private boolean setWatched;
    private boolean addToMylist = false;
    private boolean advancedMode;
    private boolean moveTypeUseFolder;
    private String tagSystemCode;
    private boolean deleteDuplicateFiles;

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

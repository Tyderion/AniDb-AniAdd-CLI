package aniAdd.config;


/**
 * Created by Archie on 23.12.2015.
 */
public class AniConfiguration {

    private boolean enableFileMove;
    private boolean enableFileRenaming;
    private String moveToFolder;
    private boolean overwriteMLEntries;
    private boolean recursivelyDeleteEmptyFolders;
    private boolean renameFiles = false;
    private boolean renameRelatedFiles;
    private boolean renameTypeAniDBFileName;
    private StorageType setStorageType;
    private String folderToLoad;
    private boolean setWatched;
    private boolean addToMylist = false;
    private boolean advancedMode;
    private boolean moveTypeUseFolder;
    private String tagSystemCode;
    private boolean deleteDuplicateFiles;
    protected String directory;

    public boolean enableFileMove() {
        return enableFileMove;
    }

    public void setEnableFileMove(boolean enableFileMove) {
        this.enableFileMove = enableFileMove;
    }

    public boolean enableFileRenaming() {
        return enableFileRenaming;
    }

    public void setEnableFileRenaming(boolean enableFileRenaming) {
        this.enableFileRenaming = enableFileRenaming;
    }

    public String moveToFolder() {
        return moveToFolder;
    }

    public void setMoveToFolder(String moveToFolder) {
        this.moveToFolder = moveToFolder;
    }

    public boolean overwriteMLEntries() {
        return overwriteMLEntries;
    }

    public void setOverwriteMLEntries(boolean overwriteMLEntries) {
        this.overwriteMLEntries = overwriteMLEntries;
    }

    public boolean recursivelyDeleteEmptyFolders() {
        return recursivelyDeleteEmptyFolders;
    }

    public void setRecursivelyDeleteEmptyFolders(boolean recursivelyDeleteEmptyFolders) {
        this.recursivelyDeleteEmptyFolders = recursivelyDeleteEmptyFolders;
    }

    public boolean renameFiles() {
        return renameFiles;
    }

    public void setRenameFiles(boolean renameFiles) {
        this.renameFiles = renameFiles;
    }

    public boolean renameRelatedFiles() {
        return renameRelatedFiles;
    }

    public void setRenameRelatedFiles(boolean renameRelatedFiles) {
        this.renameRelatedFiles = renameRelatedFiles;
    }

    public boolean renameTypeAniDBFileName() {
        return renameTypeAniDBFileName;
    }

    public void setRenameTypeAniDBFileName(boolean renameTypeAniDBFileName) {
        this.renameTypeAniDBFileName = renameTypeAniDBFileName;
    }

    public StorageType storageType() {
        return setStorageType;
    }

    public void setSetStorageType(StorageType setStorageType) {
        this.setStorageType = setStorageType;
    }

    public String folderToLoad() {
        return folderToLoad;
    }

    public void setFolderToLoad(String folderToLoad) {
        this.folderToLoad = folderToLoad;
    }

    public boolean setWatched() {
        return setWatched;
    }

    public void setSetWatched(boolean setWatched) {
        this.setWatched = setWatched;
    }

    public boolean addToMylist() {
        return addToMylist;
    }

    public void setAddToMylist(boolean addToMylist) {
        this.addToMylist = addToMylist;
    }

    public boolean advancedMode() {
        return advancedMode;
    }

    public void setAdvancedMode(boolean advancedMode) {
        this.advancedMode = advancedMode;
    }

    public boolean moveTypeUseFolder() {
        return moveTypeUseFolder;
    }

    public void setMoveTypeUseFolder(boolean moveTypeUseFolder) {
        this.moveTypeUseFolder = moveTypeUseFolder;
    }

    public String tagSystemCode() {
        return tagSystemCode;
    }

    public void setTagSystemCode(String tagSystemCode) {
        this.tagSystemCode = tagSystemCode;
    }

    public boolean deleteDuplicateFiles() {
        return deleteDuplicateFiles;
    }

    public void setDeleteDuplicateFiles(boolean deleteDuplicateFiles) {
        this.deleteDuplicateFiles = deleteDuplicateFiles;
    }

    public String directory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
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

package aniAdd.config;


/**
 * Created by Archie on 23.12.2015.
 */
public abstract class AniConfiguration {
    protected enum StorageType {
        UNKOWN, INTERNAL, EXTERNAL, DELETE
    }

    protected boolean mEnableFileMove;

    protected boolean mEnableFileRenaming;
    protected String mMoveToFolder;
    protected boolean mOverwriteMLEntries;
    protected boolean mRecursivelyDeleteEmptyFolders;
    protected boolean mRenameFiles;
    protected boolean mRenameRelatedFiles;
    protected boolean mRenameTypeAniDBFileName;
    protected StorageType mSetStorageType;

    protected String mFolderToLoad;
    protected boolean mSetWatched;

    protected boolean mAddToMylist;
    protected boolean mAdvancedMode;
    protected String mTestString;
    protected boolean mMoveTypeUseFolder;
    protected String mTagSystemCode;

    public boolean isAddToMylist() {
        return mAddToMylist;
    }

    public void setAddToMylist(boolean addToMylist) {
        mAddToMylist = addToMylist;
    }

    public boolean isAdvancedMode() {
        return mAdvancedMode;
    }

    public void setAdvancedMode(boolean advancedMode) {
        mAdvancedMode = advancedMode;
    }

    public String getTestString() {
        return mTestString;
    }

    public void setTestString(String testString) {
        mTestString = testString;
    }

    public String getTagSystemCode() {
        return mTagSystemCode;
    }

    public void setTagSystemCode(String tagSystemCode) {
        mTagSystemCode = tagSystemCode;
    }

    public boolean isMoveTypeUseFolder() {
        return mMoveTypeUseFolder;
    }


    public boolean isEnableFileRenaming() {
        return mEnableFileRenaming;
    }

    public void setEnableFileRenaming(boolean enableFileRenaming) {
        mEnableFileRenaming = enableFileRenaming;
    }

    public String getMoveToFolder() {
        return mMoveToFolder;
    }

    public void setMoveToFolder(String moveToFolder) {
        mMoveToFolder = moveToFolder;
    }

    public boolean isOverwriteMLEntries() {
        return mOverwriteMLEntries;
    }

    public void setOverwriteMLEntries(boolean overwriteMLEntries) {
        mOverwriteMLEntries = overwriteMLEntries;
    }

    public boolean isRecursivelyDeleteEmptyFolders() {
        return mRecursivelyDeleteEmptyFolders;
    }

    public void setRecursivelyDeleteEmptyFolders(boolean recursivelyDeleteEmptyFolders) {
        mRecursivelyDeleteEmptyFolders = recursivelyDeleteEmptyFolders;
    }

    public boolean isRenameFiles() {
        return mRenameFiles;
    }

    public void setRenameFiles(boolean renameFiles) {
        mRenameFiles = renameFiles;
    }

    public boolean isRenameRelatedFiles() {
        return mRenameRelatedFiles;
    }

    public void setRenameRelatedFiles(boolean renameRelatedFiles) {
        mRenameRelatedFiles = renameRelatedFiles;
    }

    public boolean isRenameTypeAniDBFileName() {
        return mRenameTypeAniDBFileName;
    }

    public void setRenameTypeAniDBFileName(boolean renameTypeAniDBFileName) {
        mRenameTypeAniDBFileName = renameTypeAniDBFileName;
    }

    public StorageType getSetStorageType() {
        return mSetStorageType;
    }

    public void setSetStorageType(StorageType setStorageType) {
        mSetStorageType = setStorageType;
    }

    public void setMoveTypeUseFolder(boolean moveTypeUseFolder) {
        mMoveTypeUseFolder = moveTypeUseFolder;
    }

    public boolean isEnableFileMove() {
        return mEnableFileMove;
    }

    public void setEnableFileMove(boolean enableFileMove) {
        mEnableFileMove = enableFileMove;
    }

    public String getFolderToLoad() {
        return mFolderToLoad;
    }

    public void setFolderToLoad(String folderToLoad) {
        mFolderToLoad = folderToLoad;
    }

    public boolean isSetWatched() {
        return mSetWatched;
    }

    public void setSetWatched(boolean setWatched) {
        mSetWatched = setWatched;
    }


}

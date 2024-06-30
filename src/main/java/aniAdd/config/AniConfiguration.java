package aniAdd.config;


import java.lang.reflect.Field;
import java.util.*;

/**
 * Created by Archie on 23.12.2015.
 */
public class AniConfiguration {

    private Value<Boolean> mEnableFileMove;
    private Value<Boolean> mEnableFileRenaming;
    private Value<String> mMoveToFolder;
    private Value<Boolean> mOverwriteMLEntries;
    private Value<Boolean> mRecursivelyDeleteEmptyFolders;
    private Value<Boolean> mRenameFiles = new Value<>(SettingKey.RenameFiles, false);
    private Value<Boolean> mRenameRelatedFiles;
    private Value<Boolean> mRenameTypeAniDBFileName;
    private Value<StorageType> mSetStorageType;

    private Value<String> mFolderToLoad;
    private Value<Boolean> mSetWatched;

    private Value<Boolean> mAddToMylist = new Value<>(SettingKey.AddToMylist, false);
    private Value<Boolean> mAdvancedMode;
    private Value<Boolean> mMoveTypeUseFolder;
    private Value<String> mTagSystemCode;
    private Value<Boolean> mDeleteDuplicateFiles;

    protected String mDirectory;

    public String getDirectory() {
        return mDirectory;
    }

    public void setDirectory(String directory) {
        mDirectory = directory;
    }

    public boolean isAddToMylist() {
        return mAddToMylist.value;
    }

    public void setAddToMylist(boolean addToMylist) {
        mAddToMylist = new Value<>(SettingKey.AddToMylist, addToMylist);
    }

    public boolean isAdvancedMode() {
        return mAdvancedMode.value;
    }

    public void setAdvancedMode(boolean advancedMode) {
        mAdvancedMode = new Value<>(SettingKey.AdvancedMode, advancedMode);
    }

    public String getTagSystemCode() {
        return mTagSystemCode.value;
    }

    public void setTagSystemCode(String tagSystemCode) {
        mTagSystemCode = new Value<>(SettingKey.TagSystemCode, tagSystemCode);
    }

    public boolean shouldDeleteDuplicateFiles() {
        return mDeleteDuplicateFiles.value;
    }

    public void setDeleteDuplicateFiles(boolean deleteDuplicateFiles) {
        mDeleteDuplicateFiles = new Value<>(SettingKey.DeleteDuplicateFiles, deleteDuplicateFiles);
    }

    public boolean isMoveTypeUseFolder() {
        return mMoveTypeUseFolder.value;
    }


    public boolean isEnableFileRenaming() {
        return mEnableFileRenaming.value;
    }

    public void setEnableFileRenaming(boolean enableFileRenaming) {
        mEnableFileRenaming = new Value<>(SettingKey.EnableFileRenaming, enableFileRenaming);
    }

    public String getMoveToFolder() {
        return mMoveToFolder.value;
    }

    public void setMoveToFolder(String moveToFolder) {
        mMoveToFolder = new Value<>(SettingKey.MoveToFolder, moveToFolder);
    }

    public boolean isOverwriteMLEntries() {
        return mOverwriteMLEntries.value;
    }

    public void setOverwriteMLEntries(boolean overwriteMLEntries) {
        mOverwriteMLEntries = new Value<>(SettingKey.OverwriteMLEntries, overwriteMLEntries);
    }

    public boolean isRecursivelyDeleteEmptyFolders() {
        return mRecursivelyDeleteEmptyFolders.value;
    }

    public void setRecursivelyDeleteEmptyFolders(boolean recursivelyDeleteEmptyFolders) {
        mRecursivelyDeleteEmptyFolders = new Value<>(SettingKey.RecursivelyDeleteEmptyFolders, recursivelyDeleteEmptyFolders);
    }

    public boolean isRenameFiles() {
        return mRenameFiles.value;
    }

    public void setRenameFiles(boolean renameFiles) {
        mRenameFiles = new Value<>(SettingKey.RenameFiles, renameFiles);
    }

    public boolean isRenameRelatedFiles() {
        return mRenameRelatedFiles.value;
    }

    public void setRenameRelatedFiles(boolean renameRelatedFiles) {
        mRenameRelatedFiles = new Value<>(SettingKey.RenameRelatedFiles, renameRelatedFiles);
    }

    public boolean isRenameTypeAniDBFileName() {
        return mRenameTypeAniDBFileName.value;
    }

    public void setRenameTypeAniDBFileName(boolean renameTypeAniDBFileName) {
        mRenameTypeAniDBFileName = new Value<>(SettingKey.RenameTypeAniDBFileName, renameTypeAniDBFileName);
    }

    public StorageType getSetStorageType() {
        return mSetStorageType.value;
    }

    public void setSetStorageType(StorageType setStorageType) {
        mSetStorageType = new Value<>(SettingKey.SetStorageType, setStorageType == null ? StorageType.INTERNAL : setStorageType);
    }

    public void setMoveTypeUseFolder(boolean moveTypeUseFolder) {
        mMoveTypeUseFolder = new Value<Boolean>(SettingKey.MoveTypeUseFolder, moveTypeUseFolder);
    }

    public boolean isEnableFileMove() {
        return mEnableFileMove.value;
    }

    public void setEnableFileMove(boolean enableFileMove) {
        mEnableFileMove = new Value<Boolean>(SettingKey.EnableFileMove, enableFileMove);
    }

    public String getFolderToLoad() {
        return mFolderToLoad.value;
    }

    public void setFolderToLoad(String folderToLoad) {
        mFolderToLoad = new Value<String>(SettingKey.FolderToLoad, folderToLoad);
    }

    public boolean isSetWatched() {
        return mSetWatched.value;
    }

    public void setSetWatched(boolean setWatched) {
        mSetWatched = new Value<Boolean>(SettingKey.SetWatched, setWatched);
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


    private static class Value<T> {
        public final SettingKey mapping;
        public final T value;

        private Value(SettingKey mapping, T value) {
            this.mapping = mapping;
            this.value = value;
        }
    }

    public Map<String, Object> ToMod_Memory() {
        Map<String, Object> config = new TreeMap<>();

        Field[] fields = AniConfiguration.class.getDeclaredFields();
        for (Field f : fields) {
            Class<?> type = f.getType();
            if (type.equals(Value.class)) {
                try {
                    Value<?> theValue = (Value<?>) f.get(this);
                    String name = theValue.mapping.toString();
                    Object value = theValue.value;
                    if (value instanceof StorageType) {
                        value = ((StorageType) value).getValue();
                    }
                    config.put(name, value);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        return config;
    }
}

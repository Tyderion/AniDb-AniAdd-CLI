package aniAdd.config;


import java.lang.reflect.Field;
import java.util.*;

/**
 * Created by Archie on 23.12.2015.
 */
public class AniConfiguration {
    protected enum StorageType {
        UNKOWN(0), INTERNAL(1), EXTERNAL(2), DELETE(3), REMOTE(4);

        private final int value;

        private StorageType(int i) {
            value = i;
        }

        public int getValue() {
            return value;
        }
    }

    protected Value<Boolean> mEnableFileMove;
    protected Value<Boolean> mEnableFileRenaming;
    protected Value<String> mMoveToFolder;
    protected Value<Boolean> mOverwriteMLEntries;
    protected Value<Boolean> mRecursivelyDeleteEmptyFolders;
    protected Value<Boolean> mRenameFiles;
    protected Value<Boolean> mRenameRelatedFiles;
    protected Value<Boolean> mRenameTypeAniDBFileName;
    protected Value<StorageType> mSetStorageType;

    protected Value<String> mFolderToLoad;
    protected Value<Boolean> mSetWatched;

    protected Value<Boolean> mAddToMylist;
    protected Value<Boolean> mAdvancedMode;
    protected Value<Boolean> mMoveTypeUseFolder;
    protected Value<String> mTagSystemCode;
    protected Value<Boolean> mDeleteDuplicateFiles;

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
        mAddToMylist = new Value<Boolean>(ToMod_MemoryMapper.AddToMylist, addToMylist);
    }

    public boolean isAdvancedMode() {
        return mAdvancedMode.value;
    }

    public void setAdvancedMode(boolean advancedMode) {
        mAdvancedMode = new Value<Boolean>(ToMod_MemoryMapper.AdvancedMode, advancedMode);
    }

    public String getTagSystemCode() {
        return mTagSystemCode.value;
    }

    public void setTagSystemCode(String tagSystemCode) {
        mTagSystemCode = new Value<String>(ToMod_MemoryMapper.TagSystemCode, tagSystemCode);
    }

    public boolean shouldDeleteDuplicateFiles() {
        return mDeleteDuplicateFiles.value;
    }

    public void setDeleteDuplicateFiles(boolean deleteDuplicateFiles) {
        mDeleteDuplicateFiles = new Value<Boolean>(ToMod_MemoryMapper.DeleteDuplicateFiles, deleteDuplicateFiles);
    }

    public boolean isMoveTypeUseFolder() {
        return mMoveTypeUseFolder.value;
    }


    public boolean isEnableFileRenaming() {
        return mEnableFileRenaming.value;
    }

    public void setEnableFileRenaming(boolean enableFileRenaming) {
        mEnableFileRenaming = new Value<Boolean>(ToMod_MemoryMapper.EnableFileRenaming, enableFileRenaming);
    }

    public String getMoveToFolder() {
        return mMoveToFolder.value;
    }

    public void setMoveToFolder(String moveToFolder) {
        mMoveToFolder = new Value<String>(ToMod_MemoryMapper.MoveToFolder, moveToFolder);
    }

    public boolean isOverwriteMLEntries() {
        return mOverwriteMLEntries.value;
    }

    public void setOverwriteMLEntries(boolean overwriteMLEntries) {
        mOverwriteMLEntries = new Value<Boolean>(ToMod_MemoryMapper.OverwriteMLEntries, overwriteMLEntries);
    }

    public boolean isRecursivelyDeleteEmptyFolders() {
        return mRecursivelyDeleteEmptyFolders.value;
    }

    public void setRecursivelyDeleteEmptyFolders(boolean recursivelyDeleteEmptyFolders) {
        mRecursivelyDeleteEmptyFolders = new Value<Boolean>(ToMod_MemoryMapper.RecursivelyDeleteEmptyFolders, recursivelyDeleteEmptyFolders);
    }

    public boolean isRenameFiles() {
        return mRenameFiles.value;
    }

    public void setRenameFiles(boolean renameFiles) {
        mRenameFiles = new Value<Boolean>(ToMod_MemoryMapper.RenameFiles, renameFiles);
    }

    public boolean isRenameRelatedFiles() {
        return mRenameRelatedFiles.value;
    }

    public void setRenameRelatedFiles(boolean renameRelatedFiles) {
        mRenameRelatedFiles = new Value<Boolean>(ToMod_MemoryMapper.RenameRelatedFiles, renameRelatedFiles);
    }

    public boolean isRenameTypeAniDBFileName() {
        return mRenameTypeAniDBFileName.value;
    }

    public void setRenameTypeAniDBFileName(boolean renameTypeAniDBFileName) {
        mRenameTypeAniDBFileName = new Value<Boolean>(ToMod_MemoryMapper.RenameTypeAniDBFileName, renameTypeAniDBFileName);
    }

    public StorageType getSetStorageType() {
        return mSetStorageType.value;
    }

    public void setSetStorageType(StorageType setStorageType) {
        mSetStorageType = new Value<StorageType>(ToMod_MemoryMapper.SetStorageType, setStorageType);
    }

    public void setMoveTypeUseFolder(boolean moveTypeUseFolder) {
        mMoveTypeUseFolder = new Value<Boolean>(ToMod_MemoryMapper.MoveTypeUseFolder, moveTypeUseFolder);
    }

    public boolean isEnableFileMove() {
        return mEnableFileMove.value;
    }

    public void setEnableFileMove(boolean enableFileMove) {
        mEnableFileMove = new Value<Boolean>(ToMod_MemoryMapper.EnableFileMove, enableFileMove);
    }

    public String getFolderToLoad() {
        return mFolderToLoad.value;
    }

    public void setFolderToLoad(String folderToLoad) {
        mFolderToLoad = new Value<String>(ToMod_MemoryMapper.FolderToLoad, folderToLoad);
    }

    public boolean isSetWatched() {
        return mSetWatched.value;
    }

    public void setSetWatched(boolean setWatched) {
        mSetWatched = new Value<Boolean>(ToMod_MemoryMapper.SetWatched, setWatched);
    }

    private enum ToMod_MemoryMapper {
        EnableFileMove("GUI_EnableFileMove"),
        EnableFileRenaming("GUI_EnableFileRenaming"),
        MoveToFolder("GUI_MoveToFolder"),
        OverwriteMLEntries("GUI_OverwriteMLEntries"),
        RecursivelyDeleteEmptyFolders("GUI_RecursivelyDeleteEmptyFolders"),
        RenameFiles("GUI_RenameFiles"),
        RenameRelatedFiles("GUI_RenameRelatedFiles"),
        RenameTypeAniDBFileName("GUI_RenameTypeAniDBFileName"),
        SetStorageType("GUI_SetStorageType"),
        FolderToLoad("GUI_FolderToAddOnLoad"),
        SetWatched("GUI_SetWatched"),
        AddToMylist("GUI_AddToMyList"),
        AdvancedMode("GUI_AdvMode"),
        MoveTypeUseFolder("GUI_MoveTypeUseFolder"),
        TagSystemCode("GUI_TagSystemCode"),
        DeleteDuplicateFiles("GUI_DeleteDuplicateFiles");
        private final String representation;

        private ToMod_MemoryMapper(String s) {
            representation = s;
        }

        @Override
        public String toString() {
            return this.representation;
        }

    }

    private class Value<T> {
        public final ToMod_MemoryMapper mapping;
        public final T value;

        private Value(ToMod_MemoryMapper mapping, T value) {
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

package aniAdd.config;

public enum SettingKey {
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

    private SettingKey(String s) {
        representation = s;
    }

    @Override
    public String toString() {
        return this.representation;
    }
}
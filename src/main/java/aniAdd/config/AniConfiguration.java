package aniAdd.config;


import lombok.*;
import lombok.extern.java.Log;

import java.nio.file.Paths;

@Log
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AniConfiguration {

    /**
     * If true files will be moved to the new location
     */
    private boolean enableFileMove;
    /**
     * If true files will be renamed
     */
    @Builder.Default private boolean enableFileRenaming = false;
    /**
     * If true mylist entries will be overwritten if they already exist
     */
    private boolean overwriteMLEntries;
    /**
     * If true empty folders will be cleared up after moving files
     */
    private boolean recursivelyDeleteEmptyFolders;
    /**
     * If true related files (i.e. files with different extensions) will be renamed as well
     */
    private boolean renameRelatedFiles;
    /**
     * If true the files will be renamed to the AniDB filename
     */
    private boolean renameTypeAniDBFileName;
    /**
     * Set the storage type of the found files
     */
    @Builder.Default private StorageType setStorageType = StorageType.UNKNOWN;
    /**
     * If true the files will be marked as watched
     */
    private boolean setWatched;
    /**
     * If true the files will be added to your mylist
     */
    @Builder.Default private boolean addToMylist = false;
    /**
     * If true the files will be moved the folder specified in {@link #moveToFolder}
     */
    private boolean moveTypeUseFolder;
    /**
     * The folder to move file sto if {@link #moveTypeUseFolder} is true
     */
    private String moveToFolder;
    /**
     * The tag system to code to use to compute new file name/path
     */
    private String tagSystemCode;
    /**
     * If true duplicate files will be deleted directly
     */
    private boolean deleteDuplicateFiles;
    /**
     * The base folder to move tv shows to.
     * It's recommended to make the tag system compute a seperate folder for each series
     * Available in the tag system as "BaseTVShowFolder"
     */
    private String tvShowFolder;
    /**
     * The base folder to move movies to.
     * It's recommended to make the tag system compute a seperate folder for each movie
     * Available in the tag system as "BaseMovieFolder"
     */
    private String movieFolder;
    /**
     * The folder to move unknown files to if enabled in {@link #moveUnknownFiles}
     */
    @Builder.Default private String unknownFolder = "/unknown/";
    /**
     * If true unknown files will be moved to the folder specified in {@link #unknownFolder}
     */
    @Builder.Default private boolean moveUnknownFiles = false;

    /**
     * The folder to move duplicate files to if enabled in {@link #moveDuplicateFiles}
     */
    @Builder.Default private String duplicatesFolder = "/duplicates/";
    /**
     * If true duplicate files will be moved to the folder specified in {@link #duplicatesFolder}
     */
    @Builder.Default private boolean moveDuplicateFiles = false;

    /**
     * The port of the anidb UDP API (default = 9000), default should be correct
     */
    @Builder.Default private int anidbPort = 9000;
    /**
     * The host of the anidb UDP API (default = api.anidb.net), default should be correct
     */
    @Builder.Default private String anidbHost = "api.anidb.net";

    /**
     * The URL to the anime mapping file, maps from AniDB to TVDB
     */
    @Builder.Default private String animeMappingUrl = "https://raw.githubusercontent.com/Anime-Lists/anime-lists/master/anime-list.xml";

    public String getEpisodePath(String relativePath) {
        return Paths.get(tvShowFolder, relativePath).toString();
    }

    public String getMoviePath(String relativePath) {
        return Paths.get(movieFolder, relativePath).toString();
    }

    @Getter
    @RequiredArgsConstructor
    public enum StorageType {
        UNKNOWN(0), INTERNAL(1), EXTERNAL(2), DELETED(3), REMOTE(4), @Deprecated UNKOWN(5);
        private final int value;
    }

    public void fixStorageType() {
        // Fix typo in original version
        if (setStorageType == StorageType.UNKOWN) {
            log.warning("Please update your configuration file. The storage type 'UNKOWN' is deprecated. Please use 'UNKNOWN' instead.");
            setStorageType = StorageType.UNKNOWN;
        }
    }

}

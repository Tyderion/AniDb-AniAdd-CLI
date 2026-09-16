package config.blocks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class KodiMetadataConfig {

    private static final String DEFAULT_ANIME_MAPPING_URL = "https://raw.githubusercontent.com/Anime-Lists/anime-lists/master/anime-list.xml";
    @Builder.Default
    private boolean generate = false;

    @Builder.Default
    private Boolean syncWatchedStateFromMylist = false;

    @Builder.Default
    private String animeMappingUrl = DEFAULT_ANIME_MAPPING_URL;

    @Builder.Default
    private OverwriteConfig overwrite = OverwriteConfig.builder().build();

    private String tvDbApiKey;
    private String tmDbApiToken;

    public void removeDefaults() {
        if (animeMappingUrl.equals(DEFAULT_ANIME_MAPPING_URL)) {
            animeMappingUrl = null;
        }
        if (!syncWatchedStateFromMylist) {
            syncWatchedStateFromMylist = null;
        }
        if (overwrite != null && overwrite.isDefault()) {
            overwrite = null;
        }
    }

    /**
     * Which existing metadata files are replaced when they are already present. Everything defaults to false: existing files are kept.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class OverwriteConfig {
        /** tvshow.nfo of a series folder */
        private boolean series;
        /** &lt;video-basename&gt;.nfo of an episode */
        private boolean episodes;
        /** &lt;video-basename&gt;.nfo of a movie */
        private boolean movies;
        /** thumbnails, fanart, poster, banner and actor images */
        private boolean artwork;

        public boolean isDefault() {
            return !series && !episodes && !movies && !artwork;
        }
    }
}

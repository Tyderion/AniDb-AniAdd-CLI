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

    private String tvDbApiKey;
    private String tmDbApiToken;

    public void removeDefaults() {
        if (animeMappingUrl.equals(DEFAULT_ANIME_MAPPING_URL)) {
            animeMappingUrl = null;
        }
        if (!syncWatchedStateFromMylist) {
            syncWatchedStateFromMylist = null;
        }
    }
}

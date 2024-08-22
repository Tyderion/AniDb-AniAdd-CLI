package config;

import config.blocks.AniDbConfig;
import config.blocks.FileConfig;
import config.blocks.KodiConfig;
import config.blocks.TagsConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j

    @Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class RootConfiguration {
    @Builder.Default
    private FileConfig file = FileConfig.builder().build();
    @Builder.Default
    private AniDbConfig anidb = AniDbConfig.builder().build();
    private RunConfig run;
    @Builder.Default
    private TagsConfig tags = TagsConfig.builder().build();
    @Builder.Default
    private KodiConfig kodi = KodiConfig.builder().build();

    public void removeDefaults() {
        file.removeDefaults();
        anidb.removeDefaults();
        this.kodi.removeDefaults();
        if (this.kodi.isEmpty()) {
            this.kodi = null;
        }

        this.anidb().exitOnBan(false);
    }

}

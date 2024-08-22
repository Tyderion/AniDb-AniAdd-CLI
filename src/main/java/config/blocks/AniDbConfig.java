package config.blocks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.nio.file.Path;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class AniDbConfig {
    @Builder.Default
    private String host = "api.anidb.net";
    @Builder.Default
    private Integer port = 9000;
    private String username;
    @Builder.Default
    private Integer localPort = 3333;

    @Builder.Default
    private Boolean exitOnBan = false;
    /**
     * Rejected if set in the config file
     */
    private String password;

    @Builder.Default
    private CacheConfig cache = CacheConfig.builder().build();


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheConfig {
        @Builder.Default
        private int ttlInDays = 30;
        @Builder.Default
        private Path db = Path.of("aniAdd.sqlite");
    }

    public void removeDefaults() {
        this.password = null;
        if (host.equals("api.anidb.net")) {
            host = null;
        }
        if (port == 9000) {
            port = null;
        }
    }
}

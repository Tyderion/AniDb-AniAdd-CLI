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
public class KodiConfig {
    @Builder.Default
    private String host = "localhost";
    @Builder.Default
    private Integer port = 9090;
    @Builder.Default
    private String pathFilter = "anime";

    @Builder.Default
    private KodiMetadataConfig metadata = KodiMetadataConfig.builder().build();

    @Builder.Default
    private KodiLibraryScanConfig libraryScan = KodiLibraryScanConfig.builder().build();

    public boolean isEmpty() {
        return host == null && port == null;
    }

    public void removeDefaults() {
        metadata.removeDefaults();
        if (libraryScan != null && libraryScan.isDefault()) {
            libraryScan = null;
        }
        if (host.equals("localhost")) {
            host = null;
        }
        if (port == 9090) {
            port = null;
        }
    }
}

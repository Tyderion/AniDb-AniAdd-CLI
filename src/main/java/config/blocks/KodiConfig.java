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

    /**
     * Whether a play reported by Kodi is written back to MyList. Defaults to true, because recording what
     * you watched is the whole point of connect-to-kodi. Set it false to observe without writing, which is
     * what the local sandbox does: file.mylist.add cannot express this, since it means "add the files I am
     * scanning" and a deployment that disables it still wants its watched state synced.
     */
    @Builder.Default
    private boolean markWatched = true;

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

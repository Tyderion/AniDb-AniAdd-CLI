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
public class RenameConfig {
    @Builder.Default
    Mode mode = Mode.NONE;
    private boolean related;

    public enum Mode {
        TAGSYSTEM,
        ANIDB,
        NONE
    }
}

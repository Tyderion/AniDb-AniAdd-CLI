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
public class FileConfig {
    @Builder.Default
    private RenameConfig rename = RenameConfig.builder().build();
    @Builder.Default
    private MoveConfig move = MoveConfig.builder().build();

    public void removeDefaults() {
        this.move.removeDefaults();
    }
}

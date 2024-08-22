package config.blocks;

import config.CliConfiguration;
import lombok.*;
import lombok.experimental.Accessors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class MyListConfig {
    public String username;
    private boolean overwrite;
    private boolean add;
    @Builder.Default
    StorageType storageType = StorageType.UNKNOWN;
    private boolean watched;


    @Getter
    @RequiredArgsConstructor
    public enum StorageType {
        UNKNOWN(0), INTERNAL(1), EXTERNAL(2), DELETED(3), REMOTE(4);
        private final int value;
    }
}

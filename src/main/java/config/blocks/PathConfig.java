package config.blocks;

import lombok.*;
import lombok.experimental.Accessors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class PathConfig {
    @Singular
    private List<Single> tvShowFolders;
    @Singular
    private List<Single> movieFolders;


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Single {
        private Path path;
        private String tagSystemName;
    }

    public Optional<Path> getEpisodePath(Path relativePath) {
        return tvShowFolders.stream()
                .map(folder -> folder.path().resolve(relativePath))
                .filter(Files::exists)
                .findFirst();
    }

    public Optional<Path> getMoviePath(Path relativePath) {
        return movieFolders.stream()
                .map(folder -> folder.path().resolve(relativePath))
                .filter(Files::exists)
                .findFirst();
    }
}

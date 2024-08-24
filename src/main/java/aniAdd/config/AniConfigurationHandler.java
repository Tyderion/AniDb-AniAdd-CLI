package aniAdd.config;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import utils.config.ConfigFileHandler;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
public final class AniConfigurationHandler extends ConfigFileHandler<AniConfiguration> {

    private final String taggingSystem;

    public AniConfigurationHandler(String taggingSystem) {
        super(AniConfiguration.class);
        this.taggingSystem = taggingSystem;
    }

    @Override
    public Optional<AniConfiguration> getConfiguration(Path path, boolean useDefault) {
        val configuration = super.getConfiguration(path, useDefault);
        configuration.ifPresent(this::loadTaggingSystem);
        // Fix typo :(
        configuration.ifPresent(AniConfiguration::fixStorageType);

        return configuration;
    }

    private void loadTaggingSystem(AniConfiguration config) {
        if (taggingSystem != null) {
            try {
                String tagSystemCode = readFile(taggingSystem, Charset.defaultCharset());
                if (!Objects.equals(tagSystemCode, "")) {
                    config.setTagSystemCode(tagSystemCode);
                }
            } catch (IOException e) {
                Logger.getGlobal().log(Level.WARNING, STR."Could not read tagging system file: \{taggingSystem}");
            }
        }
    }

    private static String readFile(String path, Charset encoding)
            throws IOException {
        return String.join("\n", Files.readAllLines(Path.of(path), encoding));
    }
}

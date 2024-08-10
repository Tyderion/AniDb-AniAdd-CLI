package aniAdd.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
@RequiredArgsConstructor
public final class ConfigFileLoader {

    private final String configPath;
    private final String taggingSystem;


    public Optional<AniConfiguration> getConfiguration(boolean useDefault) {
        val configuration = loadConfiguration(useDefault);
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

    private Optional<AniConfiguration> loadConfiguration(boolean useDefault) {
        if (configPath != null) {
            ConfigFileParser<AniConfiguration> configParser =
                    new ConfigFileParser<>(configPath, AniConfiguration.class);
            return configParser.loadFromFile(useDefault);
        }
        if (useDefault) {
            log.warn("Using default configuration");
            return Optional.of(new AniConfiguration());
        }
        return Optional.empty();
    }

    private static String readFile(String path, Charset encoding)
            throws IOException {
        return String.join("\n", Files.readAllLines(Paths.get(path), encoding));
    }
}

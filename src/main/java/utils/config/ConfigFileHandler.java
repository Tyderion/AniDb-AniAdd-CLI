package utils.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class ConfigFileHandler<T> {
    private final Class<T> clazz;
    @Getter(lazy = true, value = AccessLevel.PROTECTED)
    private final ConfigFileParser<T> configParser = new ConfigFileParser<>(clazz);

    public T getConfiguration(Path path) {
        if (path == null || path.toString().isBlank()) {
            return null;
        }
        try {
            val input = new FileInputStream(path.toFile());
            return getConfigParser().load(input);
        } catch (FileNotFoundException e) {
            log.error(STR."File not found at: \{path}");
            return null;
        }
    }

    public Optional<T> getConfiguration(Path path, boolean useDefault) {
        val config = this.getConfiguration(path);
        if (useDefault && config == null) {
            log.warn("Using default configuration");
            try {
                return Optional.of(clazz.getDeclaredConstructor().newInstance());
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                     InvocationTargetException e) {
                log.error("Could not create default configuration", e);
                return Optional.empty();
            }
        }
        return Optional.ofNullable(config);
    }

    public void saveTo(Path path, T configuration) {
        try {
            if (!Files.exists(path)) {
                Writer writer = new BufferedWriter(new FileWriter(path.toFile()));
                log.info(STR."Saving config to file: \{path.toAbsolutePath()}");
                getConfigParser().dump(configuration, writer);
            }
        } catch (IOException e) {
            log.warn(STR."Could not write config to file: \{path}");
        }
    }
}

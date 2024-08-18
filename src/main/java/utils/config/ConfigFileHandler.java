package utils.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class ConfigFileHandler<T> {
    private final Class<T> clazz;
    @Getter(lazy = true, value = AccessLevel.PROTECTED)
    private final ConfigFileParser<T> configParser = new ConfigFileParser<>(clazz);

    public T getConfiguration(String path) {
        return getConfigParser().loadFromFile(path);
    }

    public Optional<T> getConfiguration(String path, boolean useDefault) {
        val config = this.getConfiguration(path);
        if (useDefault && config == null) {
            log.warn("Using default configuration");
            try {
                return Optional.of(clazz.getDeclaredConstructor().newInstance());
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                log.error("Could not create default configuration", e);
                return Optional.empty();
            }
        }
        return Optional.ofNullable(config);
    }
}

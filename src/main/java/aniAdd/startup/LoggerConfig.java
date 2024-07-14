package aniAdd.startup;

import lombok.val;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

public class LoggerConfig {
    private final static String LOGGING_FORMAT_KEY = "java.util.logging.SimpleFormatter.format";

    // For some reason the logging.properties file is not loaded correctly when not setting properties non-programmatically
    static void configureLogger() {
        try {
            val filePath = Main.class.getClassLoader().getResource("logging.properties");
            if (filePath != null) {

                val logConfig = Files.readAllLines(new File(filePath.toURI()).toPath());
                val configValues = logConfig.stream()
                        .map(String::trim)
                        .filter(s -> !s.startsWith("#") && !s.isEmpty())
                        .map(s -> s.split("="))
                        .collect(Collectors.toMap(s -> s[0], s -> Arrays.stream(s).skip(1).collect(Collectors.joining("="))));
                LogManager.getLogManager().updateConfiguration((key) -> (oldVal, newVal) ->
                        configValues.getOrDefault(key, newVal));
                if (configValues.containsKey(LOGGING_FORMAT_KEY)) {
                    System.setProperty(LOGGING_FORMAT_KEY, configValues.get(LOGGING_FORMAT_KEY));
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}

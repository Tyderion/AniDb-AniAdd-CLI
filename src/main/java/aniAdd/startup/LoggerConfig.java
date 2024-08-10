package aniAdd.startup;

import lombok.val;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

public class LoggerConfig {
    private final static String LOGGING_FORMAT_KEY = "java.util.logging.SimpleFormatter.format";

    // For some reason the logging.properties file is not loaded correctly when reading config or setting the system property
    static void configureLogger() {
        try {
            val fileName = System.getenv("LOG_CONFIG_FILE");
            if (fileName == null) {
                System.out.println("No log configuration file specified. Using default logging configuration.");
                return;
            }
            try (val configStream = new FileInputStream(fileName)) {
                val inputStreamReader = new BufferedReader(new InputStreamReader(configStream, StandardCharsets.UTF_8));
                val logConfig = inputStreamReader.lines().toList();
                val configValues = logConfig.stream()
                        .map(String::trim)
                        .filter(s -> !s.startsWith("#") && !s.isEmpty())
                        .map(s -> s.split("="))
                        .collect(Collectors.toMap(s -> s[0], s -> Arrays.stream(s).skip(1).collect(Collectors.joining("="))));
                // Make sure we get most jul (java.util.logging) logs into slf4j
                if (!configValues.containsKey(".level")) {
                    configValues.put(".level", "FINE");
                }
                if (!configValues.containsKey("java.util.logging.ConsoleHandler.level")) {
                    configValues.put("java.util.logging.ConsoleHandler.level", "FINE");
                }
                LogManager.getLogManager().updateConfiguration((key) -> (oldVal, newVal) ->
                        configValues.getOrDefault(key, newVal));
                for (val entry : configValues.entrySet()) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }

                SLF4JBridgeHandler.removeHandlersForRootLogger();
                SLF4JBridgeHandler.install();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

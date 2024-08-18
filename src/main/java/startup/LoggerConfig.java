package startup;

import lombok.val;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

public class LoggerConfig {

    // For some reason the logging.properties file is not loaded correctly when reading config or setting the system property
    static void configureLogger() {
        // Make sure we get reasonable jul (java.util.logging) logs into slf4j
        // Can be overwritten via logging override
        System.setProperty(".level", "INFO");
        System.setProperty("java.util.logging.ConsoleHandler.level", "INFO");
        try {
            val fileName = System.getenv("LOG_CONFIG_FILE");
            final Map<String, String> configValues = new HashMap<>();
            configValues.put("org.jboss.logging.provider", "slf4j");
            if (fileName == null) {
                System.out.println("No log configuration file specified. Using included logging configuration.");
            } else {
                System.out.println("Log configuration file specified. Overriding included properties.");
                try (val configStream = new FileInputStream(fileName)) {
                    val inputStreamReader = new BufferedReader(new InputStreamReader(configStream, StandardCharsets.UTF_8));
                    val logConfig = inputStreamReader.lines().toList();
                    logConfig.stream()
                            .map(String::trim)
                            .filter(s -> !s.startsWith("#") && !s.isEmpty()).map(s -> s.split("="))
                            .forEach(s -> configValues.put(s[0], Arrays.stream(s).skip(1).collect(Collectors.joining("="))));
                }
            }
            LogManager.getLogManager().updateConfiguration((key) -> (oldVal, newVal) ->
                    configValues.getOrDefault(key, newVal));
            for (val entry : configValues.entrySet()) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

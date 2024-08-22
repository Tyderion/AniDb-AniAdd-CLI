package startup.commands;

import config.CliConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import startup.validation.validators.nonblank.NonBlank;
import utils.config.ConfigFileHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Slf4j
public abstract class ConfigCommand {
    @Getter
    @NonBlank
    @CommandLine.Option(names = {"-c", "--config"}, description = "The path to the config file. Specified parameters will override values from the config file.", required = true, scope = CommandLine.ScopeType.INHERIT)
    Path configPath;

    @Getter(lazy = true)
    private final CliConfiguration configuration = loadConfiguration();

    private CliConfiguration loadConfiguration() {
        if (configPath == null) {
            val configFile = System.getenv("CONFIG_FILE");
            if (configFile == null) {
                log.error("No config file provided. Set the CONFIG_FILE environment variable or use the -r option.");
                return null;
            }
            configPath = Path.of(System.getenv("CONFIG_FILE"));
        }
        try {
            val content = Files.readString(configPath, StandardCharsets.UTF_8);
            if (content.contains("addToMylist")) {
                log.error("Old config detected. Please convert it with 'config convert' command.");
                return null;
            }
        } catch (IOException e) {
            log.error(STR."Error reading configuration file \{configPath}");
            return null;
        }
        val handler = new ConfigFileHandler<>(CliConfiguration.class);
        return handler.getConfiguration(configPath);
    }
}

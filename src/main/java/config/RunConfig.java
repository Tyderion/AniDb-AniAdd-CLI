package config;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import startup.commands.anidb.AnidbCommand;
import startup.commands.anidb.KodiWatcherCommand;
import startup.commands.anidb.ScanCommand;
import startup.commands.anidb.WatchCommand;

import java.nio.file.Path;
import java.util.*;

import static config.RunConfig.Task.SCAN;
import static config.RunConfig.Task.WATCH;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class RunConfig {
    private static final String PARAM_NAME = "path";
    private Task task;
    private Map<String, String> args = new TreeMap<>();
    String config;

    public enum Task {
        SCAN, WATCH, KODI
    }

    public List<String> toCommandArgs(Path runConfig) throws InvalidConfigException {
        if (task == null) {
            throw new InvalidConfigException("No tasks specified in the config file.");
        }
        if (EnumSet.of(SCAN, WATCH).contains(task)) {
            if (!args.containsKey(PARAM_NAME) || args.get(PARAM_NAME).isBlank()) {
                throw new InvalidConfigException("No folder specified for scan or watch task.");
            }
        }
        if (args.containsKey("password")) {
            throw new InvalidConfigException("Password must not be provided in the config file. Use the command line or env instead.");
        }

        val arguments = new ArrayList<>(List.of(AnidbCommand.getName()));
        val parameter = args.remove(PARAM_NAME);

        switch (task) {
            case KODI -> arguments.add(KodiWatcherCommand.getName());
            case WATCH -> {
                arguments.add(WatchCommand.getName());
                arguments.add(parameter);
            }
            case SCAN -> {
                arguments.add(ScanCommand.getName());
                arguments.add(parameter);
            }
        }
        if (config == null) {
            log.info(STR."Run config does not contain a config file for the command. Using run config file ('\{runConfig}') as the config file for executing command.");
            args.put("config", runConfig.toString());
        } else {
            if (Path.of(config).isAbsolute()) {
                args.put("config", config);
            } else {
                args.put("config", runConfig.getParent().resolve(config).normalize().toString());
            }
        }
        args.forEach((name, value) -> arguments.add(STR."--\{name}=\{value}"));
        return arguments;
    }

    public static class InvalidConfigException extends Exception {
        public InvalidConfigException(String message) {
            super(message);
        }
    }
}

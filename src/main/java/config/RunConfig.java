package config;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import startup.commands.anidb.*;
import utils.config.SecretsLoader;

import java.nio.file.Path;
import java.util.*;

import static config.RunConfig.Task.*;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class RunConfig {
    private static final String PARAM_NAME = "path";
    @Singular
    private Set<Task> tasks;
    private Map<String, String> args = new HashMap<>();

    public enum Task {
        SCAN, WATCH, KODI
    }

    public List<String> toCommandArgs(Path runConfig, SecretsLoader secrets) throws InvalidConfigException {
        if (tasks.isEmpty()) {
            throw new InvalidConfigException("No tasks specified in the config file.");
        }
        if (tasks.containsAll(EnumSet.of(SCAN, WATCH))) {
            log.warn("Both scan and watch tasks are enabled. Scan will be ignored as watch scans automatically.");
        }
        if (!Collections.disjoint(tasks, EnumSet.of(SCAN, WATCH))) {
            if (!args.containsKey(PARAM_NAME) || args.get(PARAM_NAME).isBlank()) {
                throw new InvalidConfigException("No folder specified for scan or watch task.");
            }
        }
        if (Collections.disjoint(tasks, EnumSet.of(WATCH, KODI))) {
            log.info("No persistent task enabled. The application will exit after all tasks are done.");
        }
        if (Collections.disjoint(tasks, EnumSet.of(KODI, SCAN))) {
            log.info("Both kodi (a persistent task) and scan (a non-persistent task) are enabled. The application will stay and listen to kodi");
        }
        if (args.containsKey("password")) {
            throw new InvalidConfigException("Password must not be provided in the config file. Use the command line or env instead.");
        }

        val arguments = new ArrayList<>(List.of(AnidbCommand.getName()));
        val parameter = args.remove(PARAM_NAME);

        if (tasks.contains(KODI)) {
            if (Collections.disjoint(tasks, EnumSet.of(WATCH, SCAN))) {
                arguments.add(KodiWatcherCommand.getName());
            } else {
                arguments.add(WatchAndKodiCommand.getName());
                arguments.add(parameter);
            }
        } else if (tasks.contains(WATCH)) {
            arguments.add(WatchCommand.getName());
            arguments.add(parameter);
        } else if (tasks.contains(SCAN)) {
            arguments.add(ScanCommand.getName());
            arguments.add(parameter);
        }
        args.forEach((name, value) -> arguments.add(STR."--\{name}=\{value}"));
        arguments.add(STR."--config=\{runConfig.toAbsolutePath()}");

        return arguments;
    }

    public static class InvalidConfigException extends Exception {
        public InvalidConfigException(String message) {
            super(message);
        }
    }
}

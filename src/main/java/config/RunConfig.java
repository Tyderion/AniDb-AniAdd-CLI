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
    private String config;

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

        val arguments = createAnidbCommand(runConfig.toString(), secrets);

        if (tasks.contains(KODI)) {
            if (Collections.disjoint(tasks, EnumSet.of(WATCH, SCAN))) {
                arguments.add(KodiWatcherCommand.getName());
                addOptions(KodiWatcherCommand.getOptions(), arguments);
            } else {
                arguments.add(WatchAndKodiCommand.getName());
                addOptions(WatchAndKodiCommand.getOptions(), arguments);
                arguments.add(args.get(PARAM_NAME));
            }
        } else if (tasks.contains(SCAN)) {
            arguments.add(ScanCommand.getName());
            addOptions(ScanCommand.getOptions(), arguments);
            arguments.add(args.get(PARAM_NAME));
        } else if (tasks.contains(WATCH)) {
            arguments.add(WatchCommand.getName());
            addOptions(WatchCommand.getOptions(), arguments);
            arguments.add(args.get(PARAM_NAME));
        }

        return arguments;
    }

    private List<String> createAnidbCommand(String runConfig, SecretsLoader secrets) throws InvalidConfigException {
        val arguments = new ArrayList<>(List.of(AnidbCommand.getName()));
        args.put("username", secrets.getSecret("ANIDB_USERNAME"));
        if (config == null) {
            log.info(STR."Run config does not contain a config file for the command. Using run config file ('\{runConfig}') as the config file for executing command.");
            args.put("config", runConfig);
        } else {
            args.put("config", config);
        }
        val password = secrets.getSecret("ANIDB_PASSWORD");
        if (password == null) {
            log.trace("ANIDB_PASSWORD environment variable not set.");
            throw new InvalidConfigException("ANIDB_PASSWORD environment variable not set.");
        }
        arguments.add(STR."--password=\{password}");
        addOptions(AnidbCommand.getOptions(), arguments);
        return arguments;
    }

    private void addOptions(List<String> options, List<String> arguments) {
        options.forEach(name -> addOption(name, arguments));
    }

    private void addOption(String name, List<String> arguments) {
        if (args.containsKey(name) && args.get(name) != null) {
            arguments.add(STR."--\{name}=\{args.get(name)}");
        }
    }

    public static class InvalidConfigException extends Exception {
        public InvalidConfigException(String message) {
            super(message);
        }
    }
}

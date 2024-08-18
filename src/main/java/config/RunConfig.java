package config;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.*;

import static config.RunConfig.Task.*;

@Slf4j
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RunConfig {
    @Singular
    private Set<Task> tasks;
    @Builder.Default
    private Map<String, String> args = new HashMap<>();

    public enum Task {
        SCAN, WATCH, KODI
    }

    public List<String> toCommandArgs(Path configPath) throws InvalidConfigException {
        if (tasks.isEmpty()) {
            throw new InvalidConfigException("No tasks specified in the config file.");
        }
        if (tasks.containsAll(EnumSet.of(SCAN, WATCH))) {
            log.warn("Both scan and watch tasks are enabled. Scan will be ignored as watch scans automatically.");
        }
        if (!Collections.disjoint(tasks, EnumSet.of(SCAN, WATCH))) {
            if (!args.containsKey("path") || args.get("path").isBlank()) {
                throw new InvalidConfigException("No folder specified for scan or watch task.");
            }
        }
        if (Collections.disjoint(tasks, EnumSet.of(WATCH, KODI))) {
            log.info("No persistent task enabled. The application will exit after all tasks are done.");
        }
        if (Collections.disjoint(tasks, EnumSet.of(KODI, SCAN))) {
            log.info("Both kodi (a persistent task) and scan (a non-persistent task) are enabled. The application will stay and listen to kodi");
        }

        val arguments = createAnidbCommand(configPath);

        if (tasks.contains(KODI)) {
            if (Collections.disjoint(tasks, EnumSet.of(WATCH, SCAN))) {
                arguments.add("connect-to-kodi");
            } else {
                arguments.add("watch-and-kodi");
                arguments.add(args.get("path"));
            }
            addKodiOptions(arguments);
        } else if (tasks.contains(SCAN)) {
            arguments.add("scan");
            arguments.add(args.get("path"));
        } else if (tasks.contains(WATCH)) {
            arguments.add("watch");
            arguments.add(args.get("path"));
            addOption("interval", arguments);
        }

        return arguments;
    }

    private List<String> createAnidbCommand(Path configPath) throws InvalidConfigException {
        val anidbUsername = System.getenv("ANIDB_USERNAME");
        val anidbPassword = System.getenv("ANIDB_PASSWORD");
        if (anidbUsername == null || anidbUsername.isBlank() || anidbPassword == null || anidbPassword.isBlank()) {
            throw new InvalidConfigException("No anidb username or password provided. Set the ANIDB_USERNAME and ANIDB_PASSWORD environment variables.");
        }
        val args = new ArrayList<>(List.of("anidb", "-u", anidbUsername, "-p", anidbPassword, "-c", configPath.toString()));
        addOption("localport", args);
        addOption("max-retries", args);
        addOption("db", args);
        addBooleanOption("exit-on-ban", args);
        return args;
    }

    private void addKodiOptions(List<String> args) throws InvalidConfigException {
        val kodiUrl = System.getenv("KODI_HOST");
        if (kodiUrl == null || kodiUrl.isBlank()) {
            throw new InvalidConfigException("No kodi host provided. Set the KODI_HOST (and optionally KODI_PORT) environment variable.");
        }
        args.add("--kodi");
        args.add(kodiUrl);
        val kodiPort = System.getenv("KODI_PORT");
        if (kodiPort != null && !kodiPort.isBlank()) {
            args.add("--port");
            args.add(kodiPort);
        }
        addOption("path-filter", args);
        addOption("interval", args);
    }

    private void addBooleanOption(String name, List<String> arguments) {
        if (args.containsKey(name)) {
            arguments.add(STR."--\{name}");
        }
    }

    private void addOption(String name, List<String> arguments) {
        if (args.containsKey(name)) {
            arguments.add(STR."--\{name}");
            arguments.add(args.get(name));
        }
    }


    public static class InvalidConfigException extends Exception {
        public InvalidConfigException(String message) {
            super(message);
        }
    }
}

package config;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import startup.commands.anidb.AnidbCommand;
import startup.commands.anidb.KodiWatcherCommand;
import startup.commands.anidb.ScanCommand;
import startup.commands.anidb.TranscodeCommand;
import startup.commands.anidb.WatchCommand;
import utils.config.ConfigFileParser;

import java.nio.file.Path;
import java.util.*;

import static config.RunConfig.Task.SCAN;
import static config.RunConfig.Task.TRANSCODE;
import static config.RunConfig.Task.WATCH;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class RunConfig {
    private static final String PARAM_NAME = "path";

    /**
     * Args whose target CLI option is Path-typed, so they get the same resolution as everything else in a
     * config file. They live in a Map<String, String> here, so they never reach the parser's path handling
     * and would otherwise resolve against the working directory: the same config would then use a different
     * cache depending on whether it was launched from the IDE or from a shell in another folder.
     * Keep in step with the Path-typed options on AnidbCommand.
     */
    private static final Set<String> PATH_ARGS = Set.of("db");
    private Task task;
    private Map<String, String> args = new TreeMap<>();
    String config;

    public enum Task {
        SCAN, WATCH, KODI, TRANSCODE
    }

    /**
     * @param runConfig the file this run block was read from. Relative paths inside the block resolve
     *                  against its directory, never against the working directory, so a run config means
     *                  the same thing whether it is launched from an IDE, a shell or a container.
     */
    public List<String> toCommandArgs(Path runConfig) throws InvalidConfigException {
        if (task == null) {
            throw new InvalidConfigException("No tasks specified in the config file.");
        }
        val configFile = runConfig.toAbsolutePath().normalize();
        val baseDir = ConfigFileParser.baseDirectoryOf(runConfig);
        if (EnumSet.of(SCAN, WATCH).contains(task)) {
            if (!args.containsKey(PARAM_NAME) || args.get(PARAM_NAME).isBlank()) {
                throw new InvalidConfigException("No folder specified for scan or watch task.");
            }
        }
        if (args.containsKey("password")) {
            throw new InvalidConfigException("Password must not be provided in the config file. Use the command line or env instead.");
        }

        // Checked on the raw value, before it is resolved and handed on as an explicit --db, which the
        // relocation check in AnidbCommand deliberately trusts.
        val moved = AnidbCommand.cacheRelocationProblem(args.get("db"), runConfig, Path.of("").toAbsolutePath());
        if (moved != null) {
            throw new InvalidConfigException(moved);
        }
        java.util.function.UnaryOperator<String> resolve =
                value -> ConfigFileParser.resolve(Path.of(value), baseDir).toString();
        val arguments = new ArrayList<>(List.of(AnidbCommand.getName()));
        val rawParameter = args.remove(PARAM_NAME);
        val parameter = rawParameter == null ? null : resolve.apply(rawParameter);

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
            case TRANSCODE -> {
                // Without a path this runs the transcoder on the queue; with one it queues that folder.
                arguments.add(TranscodeCommand.getName());
                if (parameter != null && !parameter.isBlank()) {
                    arguments.add(parameter);
                }
            }
        }
        if (config == null) {
            log.info(STR."Run config does not contain a config file for the command. Using run config file ('\{configFile}') as the config file for executing command.");
            args.put("config", configFile.toString());
        } else {
            args.put("config", resolve.apply(config));
        }
        PATH_ARGS.forEach(name -> args.computeIfPresent(name, (_, value) -> resolve.apply(value)));
        args.forEach((name, value) -> arguments.add(STR."--\{name}=\{value}"));
        return arguments;
    }

    public static class InvalidConfigException extends Exception {
        public InvalidConfigException(String message) {
            super(message);
        }
    }
}

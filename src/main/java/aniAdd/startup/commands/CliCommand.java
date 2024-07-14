package aniAdd.startup.commands;

import aniAdd.config.AniConfiguration;
import aniAdd.config.ConfigFileParser;
import aniAdd.startup.commands.anidb.AnidbCommand;
import aniAdd.startup.commands.config.ConfigCommand;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@CommandLine.Command(name = "aniadd-cli.jar",
        mixinStandardHelpOptions = true,
        version = "1.0",
        scope = CommandLine.ScopeType.INHERIT,
        description = "The main command.",
        subcommands = {AnidbCommand.class, ConfigCommand.class})
public class CliCommand {

    @CommandLine.Option(names = {"--tagging-system"}, description = "the path to a file containing the Tagging System definition", required = false, scope = CommandLine.ScopeType.INHERIT)
    String taggingSystem;
    @CommandLine.Option(names = {"-c", "--config"}, description = "The path to the config file. Specified parameters will override values from the config file.", required = false, scope = CommandLine.ScopeType.INHERIT)
    String configPath;

    public AniConfiguration getConfiguration() {
        AniConfiguration configuration = loadConfiguration();
        loadTaggingSystem(configuration);

        return configuration;
    }

    private void loadTaggingSystem(AniConfiguration config) {
        if (taggingSystem != null) {
            try {
                String tagSystemCode = readFile(taggingSystem, Charset.defaultCharset());
                if (!Objects.equals(tagSystemCode, "")) {
                    config.setTagSystemCode(tagSystemCode);
                }
            } catch (IOException e) {
                Logger.getGlobal().log(Level.WARNING, STR."Could not read tagging system file: \{taggingSystem}");
            }
        }
    }

    private AniConfiguration loadConfiguration() {
        if (configPath != null) {
            ConfigFileParser<AniConfiguration> configParser =
                    new ConfigFileParser<>(configPath, AniConfiguration.class);
            return configParser.loadFromFile();
        }
        Logger.getGlobal().log(Level.WARNING, "Using default configuration");
        return new AniConfiguration();
    }

    private static String readFile(String path, Charset encoding)
            throws IOException {
        return String.join("\n", Files.readAllLines(Paths.get(path), encoding));
    }

}

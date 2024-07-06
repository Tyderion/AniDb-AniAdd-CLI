package aniAdd.startup.commands;

import aniAdd.AniAdd;
import aniAdd.Communication;
import aniAdd.IAniAdd;
import aniAdd.Modules.IModule;
import aniAdd.config.AniConfiguration;
import aniAdd.config.ConfigFileParser;
import lombok.val;
import picocli.CommandLine;
import udpApi.Mod_UdpApi;
import util.StringHelper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

@CommandLine.Command(name = "cli",
        mixinStandardHelpOptions = true,
        version = "1.0",
        scope = CommandLine.ScopeType.INHERIT,
        description = "The main command.",
        subcommands = {ScanCommand.class, ServerCommand.class, SaveConfigurationCommand.class})
public class CliCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-u", "--username"}, description = "The AniDB username", required = true)
    String username;
    @CommandLine.Option(names = {"-p", "--password"}, description = "The AniDB password", required = true)
    String password;
    @CommandLine.Option(names = {"--tagging-system"}, description = "the path to a file containing the Tagging System definition", required = false)
    String taggingSystem;
    @CommandLine.Option(names = {"-c", "--config"}, description = "The path to the config file. Specified parameters will override values from the config file.", required = false)
    String configPath;

    @Override
    public Integer call() throws Exception {
        System.out.println("Please specify a subcommand. Use --help to see the available commands.");
        return 0;
    }

    AniConfiguration getConfiguration() {
        AniConfiguration configuration = loadConfiguration();
        loadTaggingSystem(configuration);

        return configuration;
    }

    IAniAdd initializeAniAdd() {
        val aniAdd = new AniAdd(getConfiguration());


        aniAdd.addComListener(comEvent -> {
            if (comEvent.EventType() == Communication.CommunicationEvent.EventType.Information) {
                if (comEvent.Params(0) == IModule.eModState.Initialized) {
                    Mod_UdpApi api = aniAdd.GetModule(Mod_UdpApi.class);
                    api.setPassword(password);
                    api.setUsername(username);

                    api.authenticate();
                }
            }
        });

        aniAdd.Start();
        return aniAdd;
    }

    private void loadTaggingSystem(AniConfiguration config) {
        if (taggingSystem != null) {
            try {
                String tagSystemCode = StringHelper.readFile(taggingSystem, Charset.defaultCharset());
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

}

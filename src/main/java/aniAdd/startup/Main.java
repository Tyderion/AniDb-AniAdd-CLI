package aniAdd.startup;

import aniAdd.Modules.IModule;
import aniAdd.*;
import aniAdd.config.AniConfiguration;
import aniAdd.config.ConfigFileParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.*;
import udpApi.Mod_UdpApi;
import util.StringHelper;

/**
 * @author Arokh
 */
public class Main {

    static String username, session, password, autopass;
    static AniAdd aniAdd;

    private static class AOMOptions {

        public static String sCliHeader = "Use AniAdd from the commandline.\n\n";

        public static AOMOption directory = new AOMOption("d", "directory", "directory", true, "PATH", true);
        public static AOMOption taggingSystem = new AOMOption(null, "tagging-system", "the path to a file containing the Tagging System definition", true, "PATH", false);
        public static AOMOption username = new AOMOption("u", "username", "username", true, "USERNAME", true);
        public static AOMOption password = new AOMOption("p", "password", "password", true, "PASSWORD", true);
        public static AOMOption help = new AOMOption("h", "help", "print this help message", false, null, false);
        public static AOMOption config = new AOMOption("c", "config", "the path to the config file. Specified parameters will override values from the config file.", true, "FILEPATH", false);
        public static AOMOption save = new AOMOption("s", "save", "save the options to a new file which then can be edited (manually) and loaded by using -c", true, "FILENAME", false);

        public static Options toCliOptions() {
            Options options = new Options();
            options.addOption(AOMOptions.directory.toOption());
            options.addOption(AOMOptions.taggingSystem.toOption());
            options.addOption(AOMOptions.username.toOption());
            options.addOption(AOMOptions.password.toOption());
            options.addOption(AOMOptions.config.toOption());
            options.addOption(AOMOptions.save.toOption());
            return options;
        }

        public static Options toHelpOnlyOptions() {
            Options options = new Options();
            options.addOption(AOMOptions.help.toOption());
            return options;
        }
    }

    private static final Options sCliOptions = AOMOptions.toCliOptions();
    private static final Options sHelpOptions = AOMOptions.toHelpOnlyOptions();

    private static String getCliOption(@NotNull CommandLine cmd, AOMOption option, String defaultValue) {
        return cmd.getOptionValue(option.getName(), defaultValue);
    }

    private static boolean hasCliOption(@NotNull CommandLine cmd, AOMOption option) {
        return cmd.hasOption(option.getName());
    }

    public static void main(String[] args) {



        CommandLineParser parser = new DefaultParser();
        CommandLine help = null;
        try {
            help = parser.parse(sHelpOptions, args, true);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }

        if (hasCliOption(help, AOMOptions.help)) {
            // automatically generate the help statement
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("aom", AOMOptions.sCliHeader, sCliOptions, "", true);
            System.exit(0);
        }

        CommandLine cmd = null;
        try {
            cmd = parser.parse(sCliOptions, args, true);
            username = getCliOption(cmd, AOMOptions.username, null);
            password = getCliOption(cmd, AOMOptions.password, null);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }



        AniConfiguration config;

        // Load optional Configuration File
        if (hasCliOption(cmd, AOMOptions.config)) {
            String path = getCliOption(cmd, AOMOptions.config, "");
            ConfigFileParser<AniConfiguration> configParser =
                    new ConfigFileParser<>(path, AniConfiguration.class);

            config = configParser.loadFromFile();

        } else {
            Logger.getGlobal().log(Level.WARNING, "Using default configuration");
            config = new AniConfiguration();
        }

        // Load optional TagSystem File
        if (hasCliOption(cmd, AOMOptions.taggingSystem)) {
            String tagSystem = getCliOption(cmd, AOMOptions.taggingSystem, null);
            if (tagSystem != null) {
                try {
                    String tagSystemCode = StringHelper.readFile(tagSystem, Charset.defaultCharset());
                    if (!Objects.equals(tagSystemCode, "")) {
                        config.setTagSystemCode(tagSystemCode);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

        // Save Configuration File
//        config.setDirectory(getCliOption(cmd, AOMOptions.directory, "."));
        if (hasCliOption(cmd, AOMOptions.save)) {
            String path = getCliOption(cmd, AOMOptions.save, "");
            try {
                ConfigFileParser<AniConfiguration> configParser =
                        new ConfigFileParser<>(path, AniConfiguration.class);
                configParser.saveToFile(config);
                Logger.getGlobal().log(Level.INFO, "Finished wiritng config to file: " + path);
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.exit(0);
        }
        aniAdd = new AniAdd(config);


        CommandLine finalCmd = cmd;
        aniAdd.addComListener(comEvent -> {
            if (comEvent.EventType() == Communication.CommunicationEvent.EventType.Information) {
                if (comEvent.Params(0) == IModule.eModState.Initialized) {
                    Initialize();
                    aniAdd.ProcessDirectory(getCliOption(finalCmd, AOMOptions.directory, "."));
                }
            }
        });

        aniAdd.Start();

    }

    private static void Initialize() {
        Mod_UdpApi api = aniAdd.GetModule(Mod_UdpApi.class);
        api.setPassword(password);
        api.setAniDBSession(session);
        api.setUsername(username);

        if (api.authenticate()) {
        } else {
        }
    }
}

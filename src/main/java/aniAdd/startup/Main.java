package aniAdd.startup;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import aniAdd.Modules.IModule;
import aniAdd.*;
import aniAdd.Communication.ComEvent;
import aniAdd.config.AniConfiguration;
import aniAdd.config.ConfigFileParser;
import aniAdd.config.XBMCDefaultConfiguration;
import com.sun.istack.internal.NotNull;
import gui.GUI;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.apache.commons.cli.*;
import udpApi.Mod_UdpApi;

/**
 * @author Arokh
 */
public class Main {

    static String username, session, password, autopass;
    static JFrame frm;
    static AniAdd aniAdd;
    private static boolean sNoGui;

    private static class AOMOptions {

        public static String sCliHeader = "Use AniAdd form the commandline.\n\n";

        public static String sBasicHeader = "";
        public static String sBasicFooter = "\nWhen using with the --no-gui flag, following options are available: \n";

        public static AOMOption directory = new AOMOption("d", "directory", "directory", true, "PATH", true);
        public static AOMOption taggingSystem = new AOMOption(null, "tagging-system", "the path to a file containing the Tagging System definition", true, "PATH", false);
        public static AOMOption username = new AOMOption("u", "username", "username", true, "USERNAME", true);
        public static AOMOption password = new AOMOption("p", "password", "password", true, "PASSWORD", true);
        public static AOMOption noGui = new AOMOption(null, "no-gui", "Use cli instead of GUI.", false, null, false);
        public static AOMOption help = new AOMOption("h", "help", "print this help message", false, null, false);
        public static AOMOption config = new AOMOption("c", "config", "the path to the config file. Specified parameters will override values from the config file.", false, "FILEPATH", false);


        public static AOMOption usernameGui = new AOMOption("u", "username", "username", true, "USERNAME", false);
        public static AOMOption passwordGui = new AOMOption("p", "password", "password", true, "PASSWORD", false);

        public static Options toCliOptions() {
            Options options = new Options();
            options.addOption(AOMOptions.directory.toOption());
            options.addOption(AOMOptions.taggingSystem.toOption());
            options.addOption(AOMOptions.username.toOption());
            options.addOption(AOMOptions.password.toOption());
            options.addOption(AOMOptions.config.toOption());
            options.addOption(AOMOptions.noGui.toOption());
            return options;
        }

        public static Options toBasicOptions() {
            Options options = new Options();
            options.addOption(AOMOptions.noGui.toOption());
            options.addOption(AOMOptions.help.toOption());
            options.addOption(AOMOptions.usernameGui.toOption());
            options.addOption(AOMOptions.passwordGui.toOption());
            return options;
        }
    }

    private static Options sCliOptions = AOMOptions.toCliOptions();
    private static Options sBasicOptions = AOMOptions.toBasicOptions();

    private static String getCliOption(@NotNull CommandLine cmd, AOMOption option, String defaultValue) {
        return cmd.getOptionValue(option.getName(), defaultValue);
    }

    private static boolean hasCliOption(@NotNull CommandLine cmd, AOMOption option) {
        return cmd.hasOption(option.getName());
    }

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        CommandLine basicCmd = null;
        try {
            basicCmd = parser.parse(sBasicOptions, args, true);
            username = getCliOption(basicCmd, AOMOptions.usernameGui, null);
            password = getCliOption(basicCmd, AOMOptions.passwordGui, null);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }

        if (hasCliOption(basicCmd, AOMOptions.help)) {
            // automatically generate the help statement
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("aom", AOMOptions.sBasicHeader, sBasicOptions, AOMOptions.sBasicFooter, true);
            formatter.printHelp("aom", AOMOptions.sCliHeader, sCliOptions, "", true);
            System.exit(0);
        }

        sNoGui = hasCliOption(basicCmd, AOMOptions.noGui);
        if (sNoGui) {
            CommandLine cmd = null;
            try {
                cmd = parser.parse(sCliOptions, args);
            } catch (ParseException e) {
                System.out.println(e.getMessage());
                System.exit(0);
            }

            if (hasCliOption(cmd, AOMOptions.config)) {
                String path = getCliOption(cmd, AOMOptions.config, "");
                ConfigFileParser<AniConfiguration, XBMCDefaultConfiguration> configParser =
                        new ConfigFileParser<AniConfiguration, XBMCDefaultConfiguration>(path, XBMCDefaultConfiguration.class);

                AniConfiguration config = configParser.loadFromFile();
                try {
                    configParser.saveToFile(config, "config.conf");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //TODO: Inject AniConfig into aniAdd
            }


            aniAdd = new AniAdd(false, getCliOption(cmd, AOMOptions.directory, "."));


            aniAdd.addComListener(new Communication.ComListener() {

                public void EventHandler(ComEvent comEvent) {
                    if (comEvent.Type() == ComEvent.eType.Information) {
                        if ((IModule.eModState) comEvent.Params(0) == IModule.eModState.Initialized) {
                            Initialize();
                        }
                    }
                }
            });

            aniAdd.Start();

//            Logger.getGlobal().log(Level.WARNING, "No Gui Mode is not implemented yet. Shutting down.");
//            System.exit(0);
        } else {
            aniAdd = new AniAdd();
            frm = new JFrame();
            try {
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
            } catch (Exception ex) {
            }        /*try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ex) { }*/

            frm.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frm.addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosing(WindowEvent e) {
                    super.windowClosing(e);
                    aniAdd.Stop();
                }
            });

            aniAdd.addComListener(new Communication.ComListener() {

                public void EventHandler(ComEvent comEvent) {
                    if (comEvent.Type() == ComEvent.eType.Information) {
                        if ((IModule.eModState) comEvent.Params(0) == IModule.eModState.Initialized) {
                            Initialize();
                        }
                    }
                }
            });

            aniAdd.Start();
        }
    }

    private static void Initialize() {
        if (!sNoGui) {
            GUI gui = (GUI) aniAdd.GetModule("MainGUI");
            if (username == null) {
                username = JOptionPane.showInputDialog(frm, "User", "");
            }
            if (password == null) {
                password = JOptionPane.showInputDialog(frm, "Password", "");
            }
            frm.setDefaultLookAndFeelDecorated(true);
            frm.add(gui);
            frm.pack();
            frm.setVisible(true);
        }
        Mod_UdpApi api = (Mod_UdpApi) aniAdd.GetModule("UdpApi");
        api.setPassword(password);
        api.setAniDBSession(session);
        api.setUsername(username);

        if (api.authenticate()) {
        } else {
        }
    }
}

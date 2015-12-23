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
    static JFrame frm = new JFrame();
    static AniAdd aniAdd;

    private static class AOMOptions {

        public static String sCliHeader = "Use AniAdd form the commandline.\n\n";

        public static String sBasicHeader = "";
        public static String sBasicFooter = "\nWhen using with the --no-gui flag, following options are available: \n";

        public static AOMOption directory = new AOMOption("d", "directory", "directory", true, "PATH", true);
        public static AOMOption username = new AOMOption("u", "username", "username", true, "USERNAME", true);
        public static AOMOption password = new AOMOption("p", "password", "password", true, "PASSWORD", true);
        public static AOMOption noGui = new AOMOption(null, "no-gui", "Use cli instead of GUI.", false, null, false);
        public static AOMOption help = new AOMOption("h", "help", "print this help message", false, null, false);
        public static AOMOption config = new AOMOption("c", "config", "the path to the config file. Specified parameters will override values from the config file.", false, "FILEPATH", false);

        public static Options toCliOptions() {
            Options options = new Options();
            options.addOption(AOMOptions.directory.toOption());
            options.addOption(AOMOptions.username.toOption());
            options.addOption(AOMOptions.password.toOption());
            options.addOption(AOMOptions.config.toOption());
            return options;
        }

        public static Options toBasicOptions() {
            Options options = new Options();
            options.addOption(AOMOptions.noGui.toOption());
            options.addOption(AOMOptions.help.toOption());
            return options;
        }
    }

    private static Options sCliOptions = AOMOptions.toCliOptions();
    private static Options sBasicOptions = AOMOptions.toBasicOptions();

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception ex) {
        }
        /*try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ex) { }*/

        aniAdd = new AniAdd();

        // create Options object
        CommandLineParser parser = new DefaultParser();
        boolean printHelp = false;
        boolean noGUi = false;
        try {
            CommandLine cmd = parser.parse(sBasicOptions, args);
            printHelp = cmd.hasOption(AOMOptions.help.getName());
            noGUi = cmd.hasOption(AOMOptions.noGui.getName());
        } catch (ParseException e) {
            e.printStackTrace();
        }

        if (printHelp) {
            // automatically generate the help statement
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("aom", AOMOptions.sBasicHeader, sBasicOptions, AOMOptions.sBasicFooter, true);
            formatter.printHelp("aom", AOMOptions.sCliHeader, sCliOptions, "", true);
            System.exit(0);
        }

        if (noGUi) {
            try {
                CommandLine cmd = parser.parse(sCliOptions, args);
            } catch (ParseException e) {
                Logger.getGlobal().log(Level.WARNING, e.getMessage());
            }
            Logger.getGlobal().log(Level.WARNING, "No Gui Mode is not implemented yet. Shutting down.");

            ConfigFileParser<AniConfiguration, XBMCDefaultConfiguration> configParser =
                    new ConfigFileParser<AniConfiguration, XBMCDefaultConfiguration>("config.conf", XBMCDefaultConfiguration.class);

            AniConfiguration config = configParser.loadFromFile("config.conf");
            try {
                configParser.saveToFile(config);

            } catch (IOException e) {
                e.printStackTrace();
            }

            System.exit(0);
        } else {

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
        GUI gui = (GUI) aniAdd.GetModule("MainGUI");
        Mod_UdpApi api = (Mod_UdpApi) aniAdd.GetModule("UdpApi");

        username = JOptionPane.showInputDialog(frm, "User", "");
        password = JOptionPane.showInputDialog(frm, "Password", "");
        api.setPassword(password);
        api.setAniDBSession(session);
        api.setUsername(username);

        if (api.authenticate()) {
        } else {
        }

        frm.setDefaultLookAndFeelDecorated(true);

        frm.add(gui);
        frm.pack();
        frm.setVisible(true);
    }
}

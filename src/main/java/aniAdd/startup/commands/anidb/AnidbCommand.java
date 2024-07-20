package aniAdd.startup.commands.anidb;

import aniAdd.AniAdd;
import aniAdd.IAniAdd;
import aniAdd.config.AniConfiguration;
import aniAdd.startup.commands.CliCommand;
import aniAdd.startup.commands.IValidate;
import fileprocessor.FileProcessor;
import lombok.extern.java.Log;
import lombok.val;
import picocli.CommandLine;
import processing.Mod_EpProcessing;
import udpapi2.UdpApi;
import udpapi2.reply.ReplyStatus;

import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
@Log
@CommandLine.Command(
        subcommands = {ScanCommand.class, ServerCommand.class, TagsCommand.class},
        name = "anidb",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "AniDb handling")
public class AnidbCommand implements IValidate {
    @CommandLine.Option(names = {"-u", "--username"}, description = "The AniDB username", required = true, scope = CommandLine.ScopeType.INHERIT)
    String username;
    @CommandLine.Option(names = {"-p", "--password"}, description = "The AniDB password", required = true, scope = CommandLine.ScopeType.INHERIT)
    String password;


    @CommandLine.Option(names = {"--localport"}, description = "The AniDB password", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "3333")
    int localPort;

    @CommandLine.Option(names= { "--exit-on-ban" }, description = "Exit the application if the user is banned", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "false")
    boolean exitOnBan;

    @CommandLine.ParentCommand
    private CliCommand parent;

    AniConfiguration getConfiguration() {
        return parent.getConfiguration();
    }

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void validate() {
        val messages = new ArrayList<String>();
        if (localPort < 1024 || localPort > 65535) {
            messages.add("Local port must be between 1024 and 65535");
        }
        if (username == null || username.isEmpty()) {
            messages.add("Username must be set and cannot be empty");
        }
        if (password == null || password.isEmpty()) {
            messages.add("Password must be set and cannot be empty");
        }
        if (!messages.isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(), String.join(System.lineSeparator(), messages));
        }
    }

    IAniAdd initializeAniAdd(boolean terminateOnCompletion, ScheduledExecutorService executorService) {
        val udpApi = new UdpApi(executorService, localPort, username, password);
        udpApi.Initialize(getConfiguration());
        if (exitOnBan) {
            udpApi.registerCallback(ReplyStatus.BANNED, _ -> {
                log.severe("User is banned. Exiting.");
                System.exit(1);
            });
        }

        val processing = new Mod_EpProcessing(getConfiguration(), udpApi, executorService);
        val fileProcessor = new FileProcessor(processing, getConfiguration(), executorService);

        return new AniAdd(getConfiguration(), udpApi, terminateOnCompletion, fileProcessor, processing, _ -> executorService.shutdownNow());
    }
}

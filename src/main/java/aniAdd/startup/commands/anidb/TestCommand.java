package aniAdd.startup.commands.anidb;

import lombok.val;
import picocli.CommandLine;
import udpapi2.UdpApi;
import udpapi2.command.FileCommand;
import udpapi2.command.PingCommand;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "test", mixinStandardHelpOptions = true, version = "1.0",
        description = "Scans the directory for files and adds them to AniDb")
public class TestCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    private AnidbCommand parent;

    @Override
    public Integer call() throws Exception {
//        val aniAdd = parent.initializeAniAdd(false);
        val udpApi = new UdpApi();
        udpApi.Initialize(null, parent.getConfiguration());
        udpApi.setUsername(parent.username);
        udpApi.setPassword(parent.password);
//        udpApi.logIn();

//        udpApi.queueCommand(PingCommand.Create());
        udpApi.queueCommand(FileCommand.Create(1, 576874222L, "eb8f6800b9d964c8cfb8feed41256230"));
        return 0;
    }
}

package aniAdd.startup.commands;

import aniAdd.startup.commands.anidb.AnidbCommand;
import aniAdd.startup.validation.validators.nonempty.NonEmpty;
import cache.Hibernator;
import lombok.val;
import picocli.CommandLine;
import udpapi.ParseReply;
import udpapi.reply.Reply;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@CommandLine.Command(name = "test")
public class TestCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        val hibernator = new Hibernator();
        hibernator.Test();
        return 0;
    }
}

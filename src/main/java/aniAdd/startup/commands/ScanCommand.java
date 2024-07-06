package aniAdd.startup.commands;

import lombok.val;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "scan", mixinStandardHelpOptions = true, version = "1.0",
        description = "Scans the directory for files and adds them to AniDb")
public class ScanCommand implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "The directory to scan.")
    private String directory;

    @CommandLine.ParentCommand
    private CliCommand parent;

    @Override
    public Integer call() throws Exception {
        val aniAdd = parent.initializeAniAdd();
        aniAdd.ProcessDirectory(directory);
        return 0;
    }
}

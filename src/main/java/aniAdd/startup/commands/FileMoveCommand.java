package aniAdd.startup.commands;

import lombok.val;
import picocli.CommandLine;
import processing.FileHandler;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "file-move",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "File handling")
public class FileMoveCommand implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "The source file")
    String source;

    @CommandLine.Parameters(index = "1", description = "The target file")
    String target;


    @Override
    public Integer call() throws Exception {
        val renamer = new FileHandler();

        if (renamer.renameFile(Path.of(source), Path.of(target))) {
            System.out.println("File moved successfully");
            return 0;
        }


        return 0;
    }
}

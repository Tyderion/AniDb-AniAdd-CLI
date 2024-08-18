package aniAdd.startup.commands.config;

import config.CliConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import utils.config.ConfigFileHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Slf4j
@CommandLine.Command(name = "new", mixinStandardHelpOptions = true, version = "1.0",
        description = "Create new configuration file with defaults.")
public class NewCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-o", "--overwrite"}, description = "Overwrite the file if it already exists.")
    private boolean overwrite;

    @CommandLine.Parameters(index = "0", description = "The path to the file to save the configuration to.")
    private Path path;

    @Override
    public Integer call() throws Exception {
        if (overwrite || !Files.exists(path)) {
            val handler = new ConfigFileHandler<>(CliConfiguration.class);
            handler.saveTo(path, CliConfiguration.builder().build());
            log.info(STR."Configuration saved to \{path}");
            return 0;
        }
        log.info(STR."File at \{path} already exists. Use -o to overwrite.");
        return 1;
    }
}
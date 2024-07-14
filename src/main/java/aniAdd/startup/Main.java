package aniAdd.startup;

import aniAdd.startup.commands.CliCommand;
import lombok.val;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

public class Main {

    private final static String LOGGING_FORMAT_KEY = "java.util.logging.SimpleFormatter.format";

    static {
        LoggerConfig.configureLogger();
    }

    private static final String LOG_LEVEL = "FINEST";

    public static void main(String[] args) {

        new picocli.CommandLine(new CliCommand()).execute(args);
    }
}

package util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * Created by Archie on 28.12.2015.
 */
public class StringHelper {
    public static String readFile(String path, Charset encoding)
            throws IOException {
        return String.join("\n", Files.readAllLines(Paths.get(path), encoding));
    }
}

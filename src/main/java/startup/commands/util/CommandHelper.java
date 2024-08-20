package startup.commands.util;

import lombok.val;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class CommandHelper {
    public static List<String> getOptions(Class<?> clazz) {
        return getOptions(clazz, Set.of());
    }
    public static List<String> getOptions(Class<?> clazz, Set<String> exclusions) {
        return Arrays.stream(clazz.getDeclaredFields()).filter(field -> field.isAnnotationPresent(CommandLine.Option.class))
                .map(field -> field.getAnnotation(CommandLine.Option.class).names())
                .map(names -> Arrays.stream(names).max(Comparator.comparingInt(String::length)).orElseThrow(() -> new IllegalStateException("No names found")))
                .filter(name -> !exclusions.contains(name))
                .map(name -> name.replace("--", ""))
                .toList();
    }

    public static String getName(Class<?> commandClass) {
        if (commandClass.isAnnotationPresent(CommandLine.Command.class)) {
            val command = commandClass.getAnnotation(CommandLine.Command.class);
            return command.name();
        }
        return null;
    }
}

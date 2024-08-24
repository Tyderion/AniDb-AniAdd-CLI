package startup.commands.util;

import lombok.val;
import picocli.CommandLine;

public class CommandHelper {
    public static String getName(Class<?> commandClass) {
        if (commandClass.isAnnotationPresent(CommandLine.Command.class)) {
            val command = commandClass.getAnnotation(CommandLine.Command.class);
            return command.name();
        }
        return null;
    }
}

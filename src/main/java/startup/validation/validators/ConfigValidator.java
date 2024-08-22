package startup.validation.validators;

import config.CliConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.val;
import picocli.CommandLine;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

@RequiredArgsConstructor
public abstract class ConfigValidator<T, U extends Annotation> {
    private final CliConfiguration configuration;

    public abstract Optional<String> validate(U annotation, Field field, Object command, Class<?> commandClass);

    protected Object getCurrentValue(String configPath) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object current = configuration;
        val parts = configPath.split("\\.");
        for (String path : parts) {
            val method = current.getClass().getDeclaredMethod(path);
            current = method.invoke(current);
        }
        return current;
    }

    protected static String getCommandLineArgumentName(Field field) {
        if (field.isAnnotationPresent(CommandLine.Option.class)) {
            val option = field.getAnnotation(CommandLine.Option.class);
            return Arrays.stream(option.names()).max(Comparator.comparingInt(String::length)).orElse("");
        }
        return "";
    }

    protected Object overrideInConfigIfNotNull(String configPath, Object value) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        val parts = configPath.split("\\.");
        val name = parts[parts.length - 1];
        Object current = configuration;
        val containerPath = Arrays.copyOfRange(parts, 0, parts.length - 1);
        for (String path : containerPath) {
            val method = current.getClass().getDeclaredMethod(path);
            current = method.invoke(current);
        }
        if (value != null) {
            val field = current.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(current, value);
        }
        return current.getClass().getDeclaredMethod(name).invoke(current);
    }
}

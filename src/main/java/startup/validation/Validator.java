package startup.validation;

import config.CliConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import startup.commands.CliCommand;
import startup.validation.config.ConfigMustBeNull;
import startup.validation.config.OverrideConfig;
import startup.validation.validators.ValidationHelpers;
import startup.validation.validators.max.Max;
import startup.validation.validators.min.Min;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.port.Port;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

@Slf4j
public final class Validator {
    private static CliConfiguration currentConfig = null;

    public static void validateAndApplyFallbacks(CommandLine.Model.CommandSpec spec) {
        val command = spec.userObject();
        val clazz = command.getClass();
        if (clazz == CliCommand.class) {
            currentConfig = ((CliCommand) command).getConfiguration();
        }
        val messages = new ArrayList<String>();

        for (Field field : clazz.getDeclaredFields()) {
            validateConfig(field, command, clazz).ifPresent(messages::add);
            overrideConfig(field, command, clazz).ifPresent(messages::add);
            ValidationHelpers.validate(field, command, Min.class).ifPresent(messages::add);
            ValidationHelpers.validate(field, command, Max.class).ifPresent(messages::add);
            ValidationHelpers.validate(field, command, NonBlank.class).ifPresent(messages::add);
            ValidationHelpers.validate(field, command, Port.class).ifPresent(messages::add);
        }
        if (!messages.isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(), String.join(System.lineSeparator(), messages));
        }
    }

    private static Optional<String> validateConfig(Field field, Object command, Class<?> commandClass) {
        if (field.isAnnotationPresent(ConfigMustBeNull.class)) {
            val annotation = field.getAnnotation(ConfigMustBeNull.class);
            try {
                val currentValue = getCurrentValue(annotation.configPath());
                if (currentValue != null) {;
                    return Optional.of(STR."Config value \{annotation.configPath()} must be null but is not. Please remove the value from the config file and use either command line argument \{getCommandLineArgumentName(field)} or env variable \{annotation.envVariableName()} to provide the value.");
                }
                field.setAccessible(true);
                overrideValue(annotation.configPath(), System.getenv(annotation.envVariableName()));
                val finalValue = overrideValue(annotation.configPath(), field.get(command));
                if (finalValue != null || !annotation.required()) {
                    return Optional.empty();
                }
                return Optional.of(STR."Required argument \{annotation.configPath()} is required but neither the command line argument \{getCommandLineArgumentName(field)} or env variable \{annotation.envVariableName()} is set");

            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                return Optional.of(STR."Field \{field.getName()} in class \{commandClass.getSimpleName()} has config fallback '\{annotation.configPath()}' but there is an error using the specified value: \{e.getMessage()}");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> overrideConfig(Field field, Object command, Class<?> commandClass) {
        if (field.isAnnotationPresent(OverrideConfig.class)) {
            val annotation = field.getAnnotation(OverrideConfig.class);
            try {
                field.setAccessible(true);
                val finalValue = overrideValue(annotation.configPath(), field, command);
                if (finalValue != null || !annotation.required()) {
                    return Optional.empty();
                }
                return Optional.of(STR."Config value \{annotation.configPath()} is required but neither the command line \{getCommandLineArgumentName(field)} nor the config file provide a value.");
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException |
                     IllegalArgumentException e) {
                return Optional.of(STR."Field \{field.getName()} in class \{commandClass.getSimpleName()} has config fallback '\{annotation.configPath()}' but there is an error using the specified value: \{e.getMessage()}");
            }
        }
        return Optional.empty();
    }

    private static String getCommandLineArgumentName(Field field) {
        if (field.isAnnotationPresent(CommandLine.Option.class)) {
            val option = field.getAnnotation(CommandLine.Option.class);
            return Arrays.stream(option.names()).max(Comparator.comparingInt(String::length)).orElse("");
        }
        return "";
    }

    private static Object overrideValue(String configPath, Field valueField, Object command) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        val commandValue = valueField.get(command);
        val newValue = overrideValue(configPath, commandValue);
        valueField.setAccessible(true);
        valueField.set(command, newValue);
        return newValue;
    }

    private static Object overrideValue(String configPath, Object value) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        val parts = configPath.split("\\.");
        val name = parts[parts.length - 1];
        Object current = currentConfig;
        val containerPath = Arrays.copyOfRange(parts, 0, parts.length - 1);
        for (String path : containerPath) {
            val method = current.getClass().getDeclaredMethod(path);
            current = method.invoke(current);
        }
        if (value != null) {
            val method = current.getClass().getDeclaredMethod(name, value.getClass());
            method.invoke(current, value);
        }
        return current.getClass().getDeclaredMethod(name).invoke(current);
    }

    private static Object getCurrentValue(String configPath) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object current = currentConfig;
        val parts = configPath.split("\\.");
        for (String path : parts) {
            val method = current.getClass().getDeclaredMethod(path);
            current = method.invoke(current);
        }
        return current;
    }
}

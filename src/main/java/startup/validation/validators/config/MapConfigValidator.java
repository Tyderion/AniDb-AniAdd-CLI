package startup.validation.validators.config;

import config.CliConfiguration;
import lombok.val;
import picocli.CommandLine;
import startup.validation.validators.ConfigValidator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

public class MapConfigValidator extends ConfigValidator<Object, MapConfig> {
    public MapConfigValidator(CliConfiguration configuration) {
        super(configuration);
    }

    @Override
    public Optional<String> validate(MapConfig annotation, Field field, Object command, Class<?> commandClass) {
        try {
            field.setAccessible(true);
            Object value = null;

            if (annotation.configMustBeNull()) {
                val currentValue = getCurrentValue(annotation.configPath());
                if (currentValue != null) {
                    return Optional.of(STR."Config value \{annotation.configPath()} must be null but is not. Please remove the value from the config file and use either command line argument \{getCommandLineArgumentName(field)} or env variable \{annotation.envVariableName()} to provide the value.");
                }
            }

            if (field.isAnnotationPresent(CommandLine.Option.class) || field.isAnnotationPresent(CommandLine.Parameters.class)) {
                // This field is a command line argument, so we need to override the config with its value
                val parameterValue = field.get(command);
                if (parameterValue == null && !annotation.envVariableName().isEmpty()) {
                    field.set(command, System.getenv(annotation.envVariableName()));
                }
                value = overrideInConfigIfNotNull(annotation.configPath(), field.get(command));
            } else {
                value = getCurrentValue(annotation.configPath());
            }
            field.set(command, value);
            if (value != null || !annotation.required()) {
                return Optional.empty();
            }

            if (annotation.configMustBeNull() && annotation.envVariableName().isBlank()) {
                return Optional.of(STR."Required argument \{annotation.configPath()} is required but the command line \{getCommandLineArgumentName(field)} provides no value.");
            } else if (annotation.configMustBeNull()) {
                return Optional.of(STR."Required argument \{annotation.configPath()} is required but neither the command line \{getCommandLineArgumentName(field)} nor the env variable \{annotation.envVariableName()} provide a value.");
            }
            if (annotation.envVariableName().isBlank()) {
                return Optional.of(STR."Required argument \{annotation.configPath()} is required but neither the command line \{getCommandLineArgumentName(field)} nor the config file provide a value.");
            }
            return Optional.of(STR."Required argument \{annotation.configPath()} is required but neither the command line \{getCommandLineArgumentName(field)} nor the env variable \{annotation.envVariableName()} nor the config provide a value.");
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | IllegalArgumentException |
                 NoSuchFieldException e) {
            return Optional.of(STR."Field \{field.getName()} in class \{commandClass.getSimpleName()} has config fallback '\{annotation.configPath()}' but there is an error using the specified value: \{e}");
        }
    }

}

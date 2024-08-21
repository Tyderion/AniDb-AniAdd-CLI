package startup.validation.validators.config;

import config.CliConfiguration;
import lombok.val;
import startup.validation.validators.ConfigValidator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

public class OverrideConfigValidator extends ConfigValidator<Object, OverridesConfig> {
    public OverrideConfigValidator(CliConfiguration configuration) {
        super(configuration);
    }

    @Override
    public Optional<String> validate(OverridesConfig annotation, Field field, Object command, Class<?> commandClass) {
        try {
            field.setAccessible(true);
            if (!annotation.envVariableName().isEmpty()) {
                overrideValue(annotation.configPath(), field, command, System.getenv(annotation.envVariableName()));
            }
            val finalValue = overrideValue(annotation.configPath(), field, command, field.get(command));
            if (finalValue != null || !annotation.required()) {
                return Optional.empty();
            }
            return Optional.of(STR."Config value \{annotation.configPath()} is required but neither the command line \{getCommandLineArgumentName(field)} nor the config file provide a value.");
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | IllegalArgumentException |
                 NoSuchFieldException e) {
            return Optional.of(STR."Field \{field.getName()} in class \{commandClass.getSimpleName()} has config fallback '\{annotation.configPath()}' but there is an error using the specified value: \{e}");
        }
    }
}

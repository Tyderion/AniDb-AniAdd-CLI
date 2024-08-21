package startup.validation.validators.config;

import config.CliConfiguration;
import lombok.val;
import startup.validation.validators.ConfigValidator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

public class ConfigMustBeNullValidator extends ConfigValidator<Object, ConfigMustBeNull> {
    public ConfigMustBeNullValidator(CliConfiguration configuration) {
        super(configuration);
    }

    @Override
    public Optional<String> validate(ConfigMustBeNull annotation, Field field, Object command, Class<?> commandClass) {
        try {
            val currentValue = getCurrentValue(annotation.configPath());
            if (currentValue != null) {
                ;
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
}

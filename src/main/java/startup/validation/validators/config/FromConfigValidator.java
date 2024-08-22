package startup.validation.validators.config;

import config.CliConfiguration;
import lombok.val;
import startup.validation.validators.ConfigValidator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

public class FromConfigValidator extends ConfigValidator<Object, FromConfig> {
    public FromConfigValidator(CliConfiguration configuration) {
        super(configuration);
    }

    @Override
    public Optional<String> validate(FromConfig annotation, Field field, Object command, Class<?> commandClass) {
        try {
            field.setAccessible(true);
            field.set(command, getCurrentValue(annotation.configPath()));

            val finalValue = overrideInConfigIfNotNull(annotation.configPath(), field.get(command));
            field.set(command, finalValue);
            if (finalValue != null || !annotation.required()) {
                return Optional.empty();
            }
            return Optional.of(STR."Required config path '\{annotation.configPath()}' is not set in the given config file");
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | NoSuchFieldException e) {
            return Optional.of(STR."Field \{field.getName()} in class \{commandClass.getSimpleName()} should be filled from config \{annotation.configPath()}, but failed: \{e.getMessage()}");
        }
    }
}

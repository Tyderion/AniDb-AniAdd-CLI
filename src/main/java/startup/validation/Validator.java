package startup.validation;

import config.RootConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import startup.validation.validators.ValidationHelpers;
import startup.validation.validators.config.MapConfig;
import startup.validation.validators.max.Max;
import startup.validation.validators.min.Min;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.port.Port;

import java.lang.reflect.Field;
import java.util.ArrayList;

@Slf4j
public final class Validator {

    public static void validate(CommandLine.Model.CommandSpec spec, RootConfiguration configuration) {
        val command = spec.userObject();
        val clazz = command.getClass();
        val messages = new ArrayList<String>();

        // Walk the hierarchy: getDeclaredFields() alone skips inherited fields, so every
        // @MapConfig declared in a parent command (all the kodi ones in KodiWatcherCommand)
        // was left unbound and blew up as an NPE the first time it was used.
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                ValidationHelpers.validateAndUpdateConfig(configuration, field, command, MapConfig.class).ifPresent(messages::add);
                ValidationHelpers.validate(field, command, Min.class).ifPresent(messages::add);
                ValidationHelpers.validate(field, command, Max.class).ifPresent(messages::add);
                ValidationHelpers.validate(field, command, NonBlank.class).ifPresent(messages::add);
                ValidationHelpers.validate(field, command, Port.class).ifPresent(messages::add);
            }
        }
        if (!messages.isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(), String.join(System.lineSeparator(), messages));
        }
    }
}

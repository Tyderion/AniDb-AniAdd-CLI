package startup.validation;

import config.CliConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import startup.validation.validators.config.MapConfig;
import startup.validation.validators.ValidationHelpers;
import startup.validation.validators.max.Max;
import startup.validation.validators.min.Min;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.port.Port;

import java.lang.reflect.Field;
import java.util.ArrayList;

@Slf4j
public final class Validator {

    public static void validate(CommandLine.Model.CommandSpec spec, CliConfiguration configuration) {
        val command = spec.userObject();
        val clazz = command.getClass();
        val messages = new ArrayList<String>();

        for (Field field : clazz.getDeclaredFields()) {
            ValidationHelpers.validateAndUpdateConfig(configuration, field, command, MapConfig.class).ifPresent(messages::add);
            ValidationHelpers.validate(field, command, Min.class).ifPresent(messages::add);
            ValidationHelpers.validate(field, command, Max.class).ifPresent(messages::add);
            ValidationHelpers.validate(field, command, NonBlank.class).ifPresent(messages::add);
            ValidationHelpers.validate(field, command, Port.class).ifPresent(messages::add);
        }
        if (!messages.isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(), String.join(System.lineSeparator(), messages));
        }
    }
}

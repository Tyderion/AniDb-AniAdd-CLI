package aniAdd.startup.validation;

import aniAdd.startup.validation.validators.ValidationHelpers;
import aniAdd.startup.validation.validators.max.Max;
import aniAdd.startup.validation.validators.min.Min;
import aniAdd.startup.validation.validators.nonempty.NonEmpty;
import aniAdd.startup.validation.validators.port.Port;
import lombok.val;
import picocli.CommandLine;

import java.lang.reflect.Field;
import java.util.ArrayList;

public final class Validator {
    public static void validate(CommandLine.Model.CommandSpec spec) {
        val command = spec.userObject();
        val clazz = command.getClass();
        val messages = new ArrayList<String>();
        for (Field field : clazz.getDeclaredFields()) {
            ValidationHelpers.validate(field, command, Min.class).ifPresent(messages::add);
            ValidationHelpers.validate(field, command, Max.class).ifPresent(messages::add);
            ValidationHelpers.validate(field, command, NonEmpty.class).ifPresent(messages::add);
            ValidationHelpers.validate(field, command, Port.class).ifPresent(messages::add);
        }
        if (!messages.isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(), String.join(System.lineSeparator(), messages));
        }
    }


}

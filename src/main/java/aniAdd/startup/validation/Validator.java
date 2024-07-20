package aniAdd.startup.validation;

import aniAdd.startup.validation.validators.NonEmptyValidator;
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
            NonEmptyValidator.validate(field, command).ifPresent(messages::add);
        }
        if (!messages.isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(), String.join(System.lineSeparator(), messages));
        }
    }


}

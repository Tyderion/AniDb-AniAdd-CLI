package aniAdd.startup.validation.validators;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Optional;

public final class NonEmptyValidator {

    @NotNull
    public static Optional<String> validate(Field field, Object spec) {
        if (field.isAnnotationPresent(aniAdd.startup.validation.NonEmpty.class)) {
            field.setAccessible(true);
            try {
                if (field.get(spec) == null) {
                    return Optional.of(STR."Field \{field.getName()} cannot be null");
                }
                if (field.get(spec) instanceof String) {
                    if (((String) field.get(spec)).isEmpty()) {
                        return Optional.of(STR."Field \{field.getName()} cannot be empty");
                    }
                }
            } catch (IllegalAccessException e) {
                return Optional.of(STR."Field \{field.getName()} cannot be accessed");
            }
        }
        return Optional.empty();
    }
}

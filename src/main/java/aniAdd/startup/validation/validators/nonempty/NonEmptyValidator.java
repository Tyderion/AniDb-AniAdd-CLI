package aniAdd.startup.validation.validators.nonempty;

import aniAdd.startup.validation.validators.IValidator;
import aniAdd.startup.validation.validators.ValidationHelpers;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Optional;

public final class NonEmptyValidator implements IValidator<String, NonEmpty> {
    @Override
    public boolean validate(String value, NonEmpty annotation) {
        return value != null && !value.isEmpty();
    }
}

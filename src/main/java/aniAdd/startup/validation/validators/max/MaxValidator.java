package aniAdd.startup.validation.validators.max;

import aniAdd.startup.validation.validators.IValidator;
import aniAdd.startup.validation.validators.ValidationHelpers;
import aniAdd.startup.validation.validators.nonempty.NonEmpty;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Optional;

public class MaxValidator implements IValidator<Number, Max> {
    public boolean validate(Number value, Max annotation) {
       return value.longValue() <= annotation.value();
    }
}

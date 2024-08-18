package startup.validation.validators.min;

import startup.validation.validators.IValidator;

public class MinValidator implements IValidator<Number, Min> {
    public boolean validate(Number value, Min annotation) {
       return value.longValue() >= annotation.value();
    }
}

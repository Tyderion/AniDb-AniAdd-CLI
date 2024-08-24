package startup.validation.validators.max;

import startup.validation.validators.IValidator;

public class MaxValidator implements IValidator<Number, Max> {
    public boolean validate(Number value, Max annotation) {
       return value.longValue() <= annotation.value();
    }
}

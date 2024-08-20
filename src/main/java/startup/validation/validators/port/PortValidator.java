package startup.validation.validators.port;

import startup.validation.validators.IValidator;

public class PortValidator implements IValidator<Number, Port> {
    public boolean validate(Number value, Port annotation) {
        return value == null ? annotation.allowNull() : value.longValue() <= 65535 && value.longValue() >= 1024;
    }
}

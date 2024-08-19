package startup.validation.validators.nonblank;

import startup.validation.validators.IValidator;

public final class NonBlankStringValidator implements IValidator<String, NonBlank>  {
    @Override
    public boolean validate(String value, NonBlank annotation) {
        return value == null ? annotation.allowNull() : !value.isBlank();
    }
}

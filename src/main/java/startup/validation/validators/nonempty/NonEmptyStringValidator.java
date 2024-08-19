package startup.validation.validators.nonempty;

import startup.validation.validators.IValidator;

public final class NonEmptyStringValidator implements IValidator<String, NonEmpty>  {
    @Override
    public boolean validate(String value, NonEmpty annotation) {
        return value == null ? annotation.allowNull() : !value.isEmpty();
    }
}

package aniAdd.startup.validation.validators.nonempty;

import aniAdd.startup.validation.validators.IValidator;

public final class NonEmptyValidator implements IValidator<String, NonEmpty> {
    @Override
    public boolean validate(String value, NonEmpty annotation) {
        return value != null && !value.isEmpty();
    }
}

package startup.validation.validators.nonempty;

import startup.validation.validators.IValidator;

import java.nio.file.Path;

public final class NonEmptyPathValidator implements IValidator<Path, NonEmpty> {
    @Override
    public boolean validate(Path value, NonEmpty annotation) {
        return value == null ? annotation.allowNull() : !value.toString().isBlank();
    }
}

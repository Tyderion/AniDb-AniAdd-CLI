package startup.validation.validators.nonblank;

import startup.validation.validators.IValidator;

import java.nio.file.Path;

public final class NonBlankPathValidator implements IValidator<Path, NonBlank> {
    @Override
    public boolean validate(Path value, NonBlank annotation) {
        return value == null ? annotation.allowNull() : !value.toString().isBlank();
    }
}

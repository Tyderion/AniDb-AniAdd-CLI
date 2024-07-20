package aniAdd.startup.validation.validators;

import aniAdd.startup.validation.validators.max.Max;

import java.lang.annotation.Annotation;

public interface IValidator<T, U extends Annotation> {
    boolean validate(T value, U annotation);
}

package aniAdd.startup.validation.validators.nonempty;

import aniAdd.startup.validation.validators.ValidationAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface NonEmpty {
    /**
     * Set the message to be displayed when the validation fails
     * {field} will be replaced with the name of the field
     */
    public String message() default "{field} cannot be empty";
}

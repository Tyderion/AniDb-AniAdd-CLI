package aniAdd.startup.validation.validators.min;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that the given field is greater than or equal to the specified value

 */
@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.FIELD})
public @interface Min {
    /**
     * Minimum value
     */
    long value();

    /**
     * Message to be displayed when validation fails
     * {field} will be replaced with the field name
     * {value} will be replaced with the value
     */
    String message() default "{field} cannot be smaller than {value}";
}

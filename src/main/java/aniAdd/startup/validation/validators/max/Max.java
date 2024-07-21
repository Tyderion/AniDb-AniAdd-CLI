package aniAdd.startup.validation.validators.max;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that the given field is less than or equal to the specified value
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.FIELD})
public @interface Max {
    /**
     * Maximum value
     */
    public long value();

    /**
     * Message to be displayed when validation fails
     * {field} will be replaced with the field name
     * {value} will be replaced with the value
     */
    public String message() default "{field} cannot be greater than {value}";
}

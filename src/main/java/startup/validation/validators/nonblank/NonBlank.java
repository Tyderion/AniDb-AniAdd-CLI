package startup.validation.validators.nonblank;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that the given field is not an empty string

 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface NonBlank {
    /**
     * Set the message to be displayed when the validation fails
     * {field} will be replaced with the name of the field
     */
    public String message() default "{field} cannot be empty";

    public boolean allowNull() default false;
}

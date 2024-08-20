package startup.validation.validators.port;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that the given field is a valid port
 * i.e. between 1024 and 65535
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.FIELD})
public @interface Port {
    /**
     * Message to be displayed when validation fails
     * {field} will be replaced with the field name
     */
    public String message() default "{field} must be between 1024 and 65535";
    public boolean allowNull() default false;
}

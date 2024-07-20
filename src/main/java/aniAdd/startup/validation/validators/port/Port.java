package aniAdd.startup.validation.validators.port;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.FIELD})
public @interface Port {
    public String message() default "{field} must be between 1024 and 65535";
}

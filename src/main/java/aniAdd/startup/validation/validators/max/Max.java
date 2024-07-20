package aniAdd.startup.validation.validators.max;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.FIELD})
public @interface Max {
    public long value();
    public String message() default "{field} cannot be greater than {value}";
}

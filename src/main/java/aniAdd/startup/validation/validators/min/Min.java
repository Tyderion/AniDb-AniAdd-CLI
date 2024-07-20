package aniAdd.startup.validation.validators.min;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.FIELD})
public @interface Min {
    public long value();
    public String message() default "{field} cannot be smaller than {value}";
}

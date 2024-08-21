package startup.validation.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.FIELD})
public @interface ConfigMustBeNull {
    String configPath();
    String envVariableName();
    boolean required() default false;
}

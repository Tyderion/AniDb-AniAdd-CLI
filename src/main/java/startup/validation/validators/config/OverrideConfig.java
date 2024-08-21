package startup.validation.validators.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.FIELD})
public @interface OverrideConfig {
    String configPath() ;
    String envVariableName() default "";
    boolean required() default false;
}



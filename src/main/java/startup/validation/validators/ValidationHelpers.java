package startup.validation.validators;

import config.CliConfiguration;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import startup.validation.validators.config.ConfigMustBeNull;
import startup.validation.validators.config.ConfigMustBeNullValidator;
import startup.validation.validators.config.OverridesConfig;
import startup.validation.validators.config.OverrideConfigValidator;
import startup.validation.validators.max.Max;
import startup.validation.validators.max.MaxValidator;
import startup.validation.validators.min.Min;
import startup.validation.validators.min.MinValidator;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.nonblank.NonBlankPathValidator;
import startup.validation.validators.nonblank.NonBlankStringValidator;
import startup.validation.validators.port.Port;
import startup.validation.validators.port.PortValidator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ValidationHelpers {
    private static final Set<String> excludedMethods = Set.of("message", "hashCode", "equals", "toString", "annotationType");

    @SuppressWarnings("rawtypes")
    private static final Map<Class<? extends Annotation>, Map<Class, IValidator>> validators = Map.of(
            NonBlank.class, Map.of(String.class, new NonBlankStringValidator(), Path.class, new NonBlankPathValidator()),
            Max.class, Map.of(Number.class, new MaxValidator()),
            Min.class, Map.of(Number.class, new MinValidator()),
            Port.class, Map.of(Number.class, new PortValidator())
    );

    @SuppressWarnings("rawtypes")
    private static final Map<Class<? extends Annotation>, Map<Class, Class<? extends ConfigValidator>>> configValidators = Map.of(
            ConfigMustBeNull.class, Map.of(Object.class, ConfigMustBeNullValidator.class),
            OverridesConfig.class, Map.of(Object.class, OverrideConfigValidator.class)
    );

    @NotNull
    public static Optional<String> validateAndUpdateConfig(CliConfiguration configuration, Field field, Object spec, Class<? extends Annotation> annotationClass) {
        if (field.isAnnotationPresent(annotationClass)) {
            val annotation = field.getAnnotation(annotationClass);
            field.setAccessible(true);
            try {
                val validator = configValidators.get(annotationClass);
                if (validator.containsKey(field.getType())) {
                    val configValidator = validator.get(field.getType()).getDeclaredConstructor(CliConfiguration.class).newInstance(configuration);
                    return configValidator.validate(annotation, field, spec, spec.getClass());
                }
                if (validator.containsKey(Object.class)) {
                    val configValidator = validator.get(Object.class).getDeclaredConstructor(CliConfiguration.class).newInstance(configuration);
                    return configValidator.validate(annotation, field, spec, spec.getClass());
                }

            } catch (IllegalAccessException | NoSuchMethodException | InstantiationException |
                     InvocationTargetException e) {
                return Optional.of(STR."Field \{field.getName()} in class \{spec.getClass().getSimpleName()} has config fallback '\{annotationClass.getSimpleName()}' but there is an error using the specified value: \{e.getMessage()}");
            }
        }
        return Optional.empty();
    }

    @NotNull
    public static Optional<String> validate(Field field, Object spec, Class<? extends Annotation> annotationClass) {
        if (field.isAnnotationPresent(annotationClass)) {
            val annotation = field.getAnnotation(annotationClass);
            field.setAccessible(true);
            try {
                val value = field.get(spec);
                if (validators.containsKey(annotationClass)) {
                    val validator = validators.get(annotationClass);
                    if (validator.containsKey(field.getType())) {
                        if (!validator.get(field.getType()).validate(value, annotation)) {
                            return Optional.of(ValidationHelpers.getValidationMessage(annotation, field.getName()));
                        }
                    }
                    if (Number.class.isAssignableFrom(field.getType()) && validator.containsKey(Number.class)) {
                        if (!validator.get(Number.class).validate(value, annotation)) {
                            return Optional.of(ValidationHelpers.getValidationMessage(annotation, field.getName()));
                        }
                    }
                    if (validator.containsKey(Object.class)) {
                        if (!validator.get(Object.class).validate(value, annotation)) {
                            return Optional.of(ValidationHelpers.getValidationMessage(annotation, field.getName()));
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                return Optional.of(ValidationHelpers.getValidationMessage(annotation, field.getName()));
            }
        }
        return Optional.empty();
    }

    private static String getValidationMessage(Object annotation, String fieldName) {
        val clazz = annotation.getClass();
        try {
            val messageMethod = clazz.getDeclaredMethod("message");
            var message = (String) messageMethod.invoke(annotation);
            if (message.contains("{field}")) {
                message = message.replace("{field}", fieldName);
            }
            return Arrays.stream(clazz.getDeclaredMethods())
                    .filter(method -> !excludedMethods.contains(method.getName()))
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .reduce(message, (acc, method) -> {
                        try {
                            return acc.replace(STR."{\{method.getName()}}", String.valueOf(method.invoke(annotation)));
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException("Error while replacing message placeholders", e);
                        }
                    }, String::join);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(STR."Please only pass valid annotations with a message property. Get \{clazz.getName()}", e);
        }
    }
}

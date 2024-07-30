package aniAdd.startup.validation.validators;

import aniAdd.startup.validation.validators.max.Max;
import aniAdd.startup.validation.validators.max.MaxValidator;
import aniAdd.startup.validation.validators.min.Min;
import aniAdd.startup.validation.validators.min.MinValidator;
import aniAdd.startup.validation.validators.nonempty.NonEmpty;
import aniAdd.startup.validation.validators.nonempty.NonEmptyValidator;
import aniAdd.startup.validation.validators.port.Port;
import aniAdd.startup.validation.validators.port.PortValidator;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ValidationHelpers {
    private static final Set<String> excludedMethods = Set.of("message", "hashCode", "equals", "toString", "annotationType");

    @SuppressWarnings("rawtypes")
    private static final Map<Class<? extends Annotation>, IValidator> validators = Map.of(
            NonEmpty.class, new NonEmptyValidator(),
            Max.class, new MaxValidator(),
            Min.class, new MinValidator(),
            Port.class, new PortValidator()
    );

    @NotNull
    public static Optional<String> validate(Field field, Object spec, Class<? extends Annotation> annotationClass) {
        if (field.isAnnotationPresent(annotationClass)) {
            val annotation = field.getAnnotation(annotationClass);
            field.setAccessible(true);
            try {
                val value = field.get(spec);
                if (validators.containsKey(annotationClass)) {
                    val validator = validators.get(annotationClass);
                    if (!validator.validate(value, annotation)) {
                        return Optional.of(ValidationHelpers.getValidationMessage(annotation, field.getName()));
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

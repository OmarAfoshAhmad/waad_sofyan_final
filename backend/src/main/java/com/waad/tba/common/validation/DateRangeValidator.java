package com.waad.tba.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Field;
import java.time.LocalDate;

/**
 * Validator for {@link ValidDateRange}.
 * Reads startField and endField via reflection and checks startDate < endDate.
 * Passes validation if either field is null (absence is allowed; @NotNull handles that separately).
 */
public class DateRangeValidator implements ConstraintValidator<ValidDateRange, Object> {

    private String startField;
    private String endField;

    @Override
    public void initialize(ValidDateRange annotation) {
        this.startField = annotation.startField();
        this.endField   = annotation.endField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            LocalDate start = getDateField(value, startField);
            LocalDate end   = getDateField(value, endField);

            if (start == null || end == null) {
                return true; // partial dates — allowed; @NotNull handles presence
            }

            boolean valid = !end.isBefore(start) && !end.isEqual(start);
            if (!valid) {
                // Report the violation on the endField instead of the class level
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode(endField)
                        .addConstraintViolation();
            }
            return valid;

        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Configuration error — fail fast so the developer notices
            throw new IllegalStateException(
                    "@ValidDateRange: could not access fields '" + startField + "' / '" + endField
                    + "' on " + value.getClass().getSimpleName(), e);
        }
    }

    private LocalDate getDateField(Object obj, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Class<?> clazz = obj.getClass();
        // Walk up the hierarchy to support subclasses
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object fieldValue = field.get(obj);
                if (fieldValue instanceof LocalDate) {
                    return (LocalDate) fieldValue;
                }
                return null;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}

package com.waad.tba.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Cross-field validation: ensures startDate < endDate when both are provided.
 *
 * Usage on class level:
 * <pre>
 * {@code
 * @ValidDateRange(startField = "startDate", endField = "endDate")
 * public class MemberCreateDto { ... }
 * }
 * </pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
@Documented
public @interface ValidDateRange {

    String message() default "تاريخ الانتهاء يجب أن يكون بعد تاريخ البداية";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Name of the start-date field in the annotated class */
    String startField() default "startDate";

    /** Name of the end-date field in the annotated class */
    String endField() default "endDate";
}

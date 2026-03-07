package com.waad.tba.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Cross-field validation: ensures contractPrice <= basePrice when both are
 * provided.
 *
 * Usage on class level:
 * 
 * <pre>
 * {@code
 * &#64;ValidPricingRange(basePriceField = "basePrice", contractPriceField = "contractPrice")
 * public class ProviderContractPricingItemCreateDto { ... }
 * }
 * </pre>
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PricingRangeValidator.class)
@Documented
public @interface ValidPricingRange {

    String message() default "سعر العقد يجب أن يكون أقل من أو يساوي السعر الأساسي";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Name of the base price field */
    String basePriceField() default "basePrice";

    /** Name of the contract price field */
    String contractPriceField() default "contractPrice";
}

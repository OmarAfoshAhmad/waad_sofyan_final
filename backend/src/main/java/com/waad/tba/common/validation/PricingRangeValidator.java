package com.waad.tba.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.lang.reflect.Field;

public class PricingRangeValidator implements ConstraintValidator<ValidPricingRange, Object> {

    private String basePriceField;
    private String contractPriceField;

    @Override
    public void initialize(ValidPricingRange annotation) {
        this.basePriceField = annotation.basePriceField();
        this.contractPriceField = annotation.contractPriceField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) return true;
        try {
            Field baseField = value.getClass().getDeclaredField(basePriceField);
            Field contractField = value.getClass().getDeclaredField(contractPriceField);
            baseField.setAccessible(true);
            contractField.setAccessible(true);

            BigDecimal base = (BigDecimal) baseField.get(value);
            BigDecimal contract = (BigDecimal) contractField.get(value);

            if (base == null || contract == null) return true;

            if (contract.compareTo(base) > 0) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode(contractPriceField)
                        .addConstraintViolation();
                return false;
            }
            return true;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return true;
        }
    }
}

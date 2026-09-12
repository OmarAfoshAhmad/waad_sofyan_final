package com.waad.tba.modules.member.exception;

import com.waad.tba.common.error.ErrorCode;
import com.waad.tba.common.exception.BusinessRuleException;

/**
 * Eligibility input is neither a card number nor a barcode, or is missing.
 *
 * A BusinessRuleException so GlobalExceptionHandler answers it (422,
 * VALIDATION_ERROR, tracking id) like every other refused input; it used to
 * be a bare RuntimeException that only this module's controller knew how to
 * turn into a response.
 */
public class InvalidEligibilityInputException extends BusinessRuleException {

    private static final String DEFAULT_MESSAGE = "رقم البطاقة أو الباركود غير صالح.";

    public InvalidEligibilityInputException() {
        this(DEFAULT_MESSAGE);
    }

    public InvalidEligibilityInputException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message != null ? message : DEFAULT_MESSAGE);
    }
}

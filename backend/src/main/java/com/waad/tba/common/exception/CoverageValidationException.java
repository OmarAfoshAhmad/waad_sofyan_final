package com.waad.tba.common.exception;

import java.math.BigDecimal;

import com.waad.tba.common.error.ErrorCode;

/**
 * Exception thrown when coverage validation fails.
 * 
 * BUSINESS SCENARIOS:
 * - Requested service not covered in benefit package
 * - Coverage limit exceeded (annual limit, per-visit limit)
 * - Service count limit reached
 * - Co-payment requirements not met
 * 
 * SMOKE TEST 1 (Service Not Covered):
 * Given: Member "Ali" has Benefit Package "Gold" that excludes dental services
 * When: Ali tries to create a claim for "DEN-001" (Dental Cleaning)
 * Then: CoverageValidationException with message "Service DEN-001 is not covered in benefit package Gold"
 * 
 * SMOKE TEST 2 (Limit Exceeded):
 * Given: Member "Sara" has annual limit of 50,000 LYD, used 48,000 LYD
 * When: Sara tries to create a claim for 5,000 LYD
 * Then: CoverageValidationException with message "Claim amount 5,000 exceeds remaining coverage limit of 2,000 LYD"
 */
public class CoverageValidationException extends BusinessRuleException {
    private static final long serialVersionUID = 1L;

    public enum CoverageIssue {
        SERVICE_NOT_COVERED,
        AMOUNT_LIMIT_EXCEEDED,
        COUNT_LIMIT_EXCEEDED,
        WAITING_PERIOD_NOT_MET,
        PRE_EXISTING_CONDITION,
        BENEFIT_EXHAUSTED
    }

    private final CoverageIssue issue;
    private final String serviceCode;
    private final BigDecimal requestedAmount;
    private final BigDecimal availableLimit;

    public CoverageValidationException(String message) {
        super(ErrorCode.COVERAGE_VALIDATION_FAILED, message);
        this.issue = CoverageIssue.SERVICE_NOT_COVERED;
        this.serviceCode = null;
        this.requestedAmount = null;
        this.availableLimit = null;
    }

    public CoverageValidationException(CoverageIssue issue, String message) {
        super(ErrorCode.COVERAGE_VALIDATION_FAILED, message);
        this.issue = issue;
        this.serviceCode = null;
        this.requestedAmount = null;
        this.availableLimit = null;
    }

    public CoverageValidationException(CoverageIssue issue, String serviceCode, String message) {
        super(ErrorCode.COVERAGE_VALIDATION_FAILED, message);
        this.issue = issue;
        this.serviceCode = serviceCode;
        this.requestedAmount = null;
        this.availableLimit = null;
    }

    public CoverageValidationException(CoverageIssue issue, BigDecimal requestedAmount, BigDecimal availableLimit) {
        super(ErrorCode.COVERAGE_VALIDATION_FAILED, buildLimitMessage(requestedAmount, availableLimit));
        this.issue = issue;
        this.serviceCode = null;
        this.requestedAmount = requestedAmount;
        this.availableLimit = availableLimit;
    }

    private static String buildLimitMessage(BigDecimal requested, BigDecimal available) {
        return String.format(
            "Claim amount %.2f LYD exceeds remaining coverage limit of %.2f LYD",
            requested != null ? requested : java.math.BigDecimal.ZERO,
            available != null ? available : java.math.BigDecimal.ZERO
        );
    }

    public CoverageIssue getIssue() {
        return issue;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getAvailableLimit() {
        return availableLimit;
    }
}

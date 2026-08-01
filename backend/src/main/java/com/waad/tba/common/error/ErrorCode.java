package com.waad.tba.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Standard set of application error codes.
 * 
 * Error codes are grouped by domain:
 * - AUTH_*: Authentication/Authorization errors
 * - USER_*: User management errors
 * - MEMBER_*: Member-related errors
 * - CLAIM_*: Claim processing errors
 * - POLICY_*: Policy validation errors
 * - COVERAGE_*: Coverage validation errors
 * - VALIDATION_*: General validation errors
 */
@Schema(description = "Standard set of application error codes")
public enum ErrorCode {
    // === Authentication/Authorization ===
    INVALID_CREDENTIALS,
    TOKEN_EXPIRED,
    ACCESS_DENIED,
    
    // === Resource Not Found ===
    USER_NOT_FOUND,
    CLAIM_NOT_FOUND,
    EMPLOYER_NOT_FOUND,
    MEMBER_NOT_FOUND,
    POLICY_NOT_FOUND,
    
    // === Member Errors ===
    MEMBER_ALREADY_EXISTS,
    MEMBER_NOT_ACTIVE,
    
    // === Policy Errors (Phase 6) ===
    /** Policy is not active on the requested date */
    POLICY_NOT_ACTIVE,
    /** Policy has no linked benefit package */
    POLICY_NO_BENEFIT_PACKAGE,
    /** Policy dates are invalid */
    POLICY_INVALID_DATES,
    
    // === Coverage Errors (Phase 6) ===
    /** Coverage validation failed - see message for details */
    COVERAGE_VALIDATION_FAILED,
    /** Requested service not covered in benefit package */
    SERVICE_NOT_COVERED,
    /** Coverage limit has been exceeded */
    COVERAGE_LIMIT_EXCEEDED,
    /** Waiting period requirement not met */
    WAITING_PERIOD_NOT_MET,
    
    // === Claim Errors (Phase 6) ===
    /** Invalid claim state transition attempted */
    INVALID_CLAIM_TRANSITION,
    /** Claim requires active policy */
    CLAIM_REQUIRES_ACTIVE_POLICY,
    /** Claim is in terminal state and cannot be modified */
    CLAIM_TERMINAL_STATE,
    /** Claim already exists with same details */
    CLAIM_DUPLICATE,
    
    // === Security & Account Management ===
    /** Account locked due to multiple failed login attempts */
    ACCOUNT_LOCKED,
    /** Email verification required before accessing resource */
    EMAIL_NOT_VERIFIED,
    /** Password reset or email verification token is invalid */
    INVALID_TOKEN,
    /** Password does not meet policy requirements */
    PASSWORD_POLICY_VIOLATION,
    
    // === General Business Rules ===
    /** General business rule violation */
    BUSINESS_RULE_VIOLATION,
    /** Entity was modified by another user since it was loaded (optimistic-lock conflict) */
    CONCURRENT_MODIFICATION,

    // === General Errors ===
    VALIDATION_ERROR,
    INTERNAL_ERROR
}


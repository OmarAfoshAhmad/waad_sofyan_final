package com.waad.tba.modules.eligibility.domain;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.providercontract.enums.EncounterType;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Eligibility Context
 * Phase E1 - Eligibility Engine
 * 
 * Aggregates all input data needed for eligibility evaluation.
 * This object is passed to each rule for evaluation.
 * 
 * The context is immutable once created - rules cannot modify it.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Data
@Builder
public class EligibilityContext {

    // ============================================
    // Request Parameters (from API)
    // ============================================

    /**
     * Unique request ID for tracking
     */
    private final String requestId;

    /**
     * Member ID to check
     */
    private final Long memberId;
    
    /**
     * BenefitPolicy ID (may be auto-resolved from member)
     */
    private final Long benefitPolicyId;

    /**
     * Provider ID (optional)
     */
    private final Long providerId;

    /**
     * Service date to check eligibility for
     */
    private final LocalDate serviceDate;

    /**
     * Service code (optional)
     */
    private final String serviceCode;

    /**
     * Medical Category ID (optional)
     */
    private final Long medicalCategoryId;

    /**
     * Medical Service ID (optional)
     */
    private final Long medicalServiceId;

    /** Explicit care setting; never inferred from the selected service. */
    @Builder.Default
    private final EncounterType encounterType = EncounterType.OUTPATIENT;

    // ============================================
    // Resolved Entities (loaded from database)
    // ============================================

    /**
     * Resolved member entity
     */
    private final Member member;
    
    /**
     * Resolved BenefitPolicy entity (canonical source)
     */
    private final BenefitPolicy benefitPolicy;

    /**
     * Resolved provider entity (optional)
     */
    private final Provider provider;

    /**
     * Resolved employer
     */
    private final Employer employer;

    // ============================================
    // Security Context
    // ============================================

    /**
     * User performing the check
     */
    private final Long checkedByUserId;

    /**
     * Username performing the check
     */
    private final String checkedByUsername;

    /**
     * Company/Organization scope of the user
     */
    private final Long companyScopeId;

    /**
     * Is the user a SUPER_ADMIN (bypass some checks)
     */
    private final boolean superAdmin;

    /**
     * IP address of the request
     */
    private final String ipAddress;

    /**
     * User agent of the request
     */
    private final String userAgent;

    // ============================================
    // Timing
    // ============================================

    /**
     * When the check was initiated
     */
    @Builder.Default
    private final LocalDateTime checkTimestamp = LocalDateTime.now();

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * Check if member was resolved
     */
    public boolean hasMember() {
        return member != null;
    }
    
    /**
     * Check if BenefitPolicy was resolved (canonical)
     */
    public boolean hasBenefitPolicy() {
        return benefitPolicy != null;
    }
    
    /**
     * Check if any policy exists (alias for hasBenefitPolicy)
     */
    public boolean hasAnyPolicy() {
        return benefitPolicy != null;
    }

    /**
     * Check if provider was resolved
     */
    public boolean hasProvider() {
        return provider != null;
    }

    /**
     * Get member status safely
     */
    public Member.MemberStatus getMemberStatus() {
        return member != null ? member.getStatus() : null;
    }

    /**
     * Get member's employer ID safely
     */
    public Long getMemberEmployerId() {
        if (employer != null) {
            return employer.getId();
        }
        return null;
    }

    /**
     * Get benefit policy's employer ID safely
     */
    public Long getPolicyEmployerId() {
        if (benefitPolicy == null) return null;
        if (benefitPolicy.getEmployer() != null) {
            return benefitPolicy.getEmployer().getId();
        }
        return null;
    }

    /**
     * Check if member belongs to the given employer
     */
    public boolean memberBelongsToEmployer(Long employerId) {
        Long memberEmployerId = getMemberEmployerId();
        return memberEmployerId != null && memberEmployerId.equals(employerId);
    }

    /**
     * Check if the service date falls within BenefitPolicy coverage period
     */
    public boolean serviceDateInCoveragePeriod() {
        if (benefitPolicy != null && serviceDate != null) {
            return benefitPolicy.isEffectiveOn(serviceDate);
        }
        return false;
    }
    
    /**
     * Check if service date is in BenefitPolicy coverage period (alias)
     */
    public boolean serviceDateInBenefitPolicyCoveragePeriod() {
        return serviceDateInCoveragePeriod();
    }
    
    /**
     * Get the effective waiting period days from BenefitPolicy.
     */
    public Integer getEffectiveWaitingPeriodDays() {
        if (benefitPolicy != null && benefitPolicy.getDefaultWaitingPeriodDays() != null) {
            return benefitPolicy.getDefaultWaitingPeriodDays();
        }
        return 0;
    }
    
    /**
     * Get BenefitPolicy status safely
     */
    public BenefitPolicy.BenefitPolicyStatus getBenefitPolicyStatus() {
        return benefitPolicy != null ? benefitPolicy.getStatus() : null;
    }

    /**
     * Get days since member enrollment
     */
    public long getDaysSinceEnrollment() {
        if (member == null || serviceDate == null) {
            return -1;
        }
        // Same fallback as WaitingPeriodRule.evaluate(): startDate is canonical,
        // joinDate is used when startDate was never set. Keeping this in sync
        // with that rule prevents a false SERVICE_DATE_BEFORE_COVERAGE denial
        // for any member missing startDate but having joinDate.
        java.time.LocalDate enrollmentDate = member.getStartDate() != null
                ? member.getStartDate()
                : member.getJoinDate();
        if (enrollmentDate == null) {
            return -1;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(enrollmentDate, serviceDate);
    }
}

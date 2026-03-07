package com.waad.tba.modules.visit.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.employer.entity.Employer;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "visits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // Denormalized employer reference (for queries/filtering)
    // Domain Architecture Decision (2026-02-13): Employer is the ONLY business entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id")
    private Employer employer;

    @Column(name = "provider_id")
    private Long providerId;

    // ==================== MEDICAL CATEGORY/SERVICE (REQUIRED) ====================
    
    /**
     * Medical Category ID (MANDATORY - ARCHITECTURAL LAW)
     * 
     * RULE: A Visit MUST have a medical category selected BEFORE a service.
     * This enables correct coverage resolution per the canonical algorithm:
     *   Category → Service → BenefitPolicyRule
     * 
     * The same medical service can exist in multiple categories with different
     * coverage rules. Category selection determines which policy rule applies.
     */
    @Column(name = "medical_category_id")
    private Long medicalCategoryId;
    
    /**
     * Medical Category Name (denormalized snapshot for display)
     */
    @Column(name = "medical_category_name", length = 200)
    private String medicalCategoryName;
    
    /**
     * Medical Service ID (must belong to the selected category)
     */
    @Column(name = "medical_service_id")
    private Long medicalServiceId;
    
    /**
     * Medical Service Code (denormalized snapshot)
     */
    @Column(name = "medical_service_code", length = 50)
    private String medicalServiceCode;
    
    /**
     * Medical Service Name (denormalized snapshot)
     */
    @Column(name = "medical_service_name", length = 200)
    private String medicalServiceName;

    private String doctorName;
    
    private String specialty;
    
    @Column(nullable = false)
    private LocalDate visitDate;
    
    private String diagnosis;
    
    private String complaint;
    
    private String treatment;
    
    @Column()
    private BigDecimal totalAmount;
    
    @Column(length = 1000)
    private String notes;
    
    @Builder.Default
    private Boolean active = true;

    /**
     * Type of visit/service location classification
     * Default: OUTPATIENT (عيادة خارجية)
     * 
     * Examples:
     * - EMERGENCY: Emergency room visits
     * - INPATIENT: Hospital admissions
     * - OUTPATIENT: Clinic visits (default)
     * - ROUTINE: Regular check-ups
     * - FOLLOW_UP: Post-treatment follow-ups
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", length = 30)
    @Builder.Default
    private VisitType visitType = VisitType.OUTPATIENT;

    /**
     * Visit status - tracks the lifecycle of the visit
     * NEW: Part of the new Pre-Authorization/Claim flow (2026-01-13)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    @Builder.Default
    private VisitStatus status = VisitStatus.REGISTERED;

    /**
     * Network status classification
     * Determines if the provider is in-network or out-of-network for this specific visit.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "network_status", length = 30)
    @Builder.Default
    private com.waad.tba.common.enums.NetworkType networkStatus = com.waad.tba.common.enums.NetworkType.IN_NETWORK;

    // ==================== NEW FLOW: Visit as central link ====================
    
    /**
     * Eligibility check ID that created this visit
     * Links visit back to the eligibility verification
     */
    @Column(name = "eligibility_check_id")
    private Long eligibilityCheckId;
    
    /**
     * Related claims created from this visit (one visit can have multiple claims)
     */
    @OneToMany(mappedBy = "visit", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<com.waad.tba.modules.claim.entity.Claim> claims = new ArrayList<>();
    
    /**
     * Related eligibility checks for this visit
     */
    @OneToMany(mappedBy = "visit", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<com.waad.tba.modules.eligibility.entity.EligibilityCheck> eligibilityChecks = new ArrayList<>();

    // ==================== AUDIT FIELDS ====================

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Version field for optimistic locking
     * Prevents concurrent status update race conditions
     * PRODUCTION HARDENING: Phase 1 - Critical Fix C6
     */
    @Version
    @Column(name = "version")
    private Long version;
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Helper method to add a claim to this visit
     */
    public void addClaim(com.waad.tba.modules.claim.entity.Claim claim) {
        claims.add(claim);
        claim.setVisit(this);
    }
    
    /**
     * Helper method to remove a claim from this visit
     */
    public void removeClaim(com.waad.tba.modules.claim.entity.Claim claim) {
        claims.remove(claim);
        claim.setVisit(null);
    }
    
    /**
     * Helper method to add an eligibility check to this visit
     */
    public void addEligibilityCheck(com.waad.tba.modules.eligibility.entity.EligibilityCheck check) {
        eligibilityChecks.add(check);
        check.setVisit(this);
    }
    
    // ==================== BUSINESS LOGIC ====================
    
    /**
     * Check if this visit allows creating a claim
     */
    public boolean allowsClaimCreation() {
        return status != null && status.allowsClaimCreation();
    }
    
    /**
     * Check if this visit allows creating a pre-authorization
     */
    public boolean allowsPreAuthCreation() {
        return status != null && status.allowsPreAuthCreation();
    }
    
    /**
     * Update status based on activity
     */
    public void updateStatusForPreAuth() {
        if (this.status == VisitStatus.REGISTERED || this.status == VisitStatus.IN_PROGRESS) {
            this.status = VisitStatus.PENDING_PREAUTH;
        }
    }
    
    public void updateStatusForClaim() {
        if (this.status != VisitStatus.CANCELLED) {
            this.status = VisitStatus.CLAIM_SUBMITTED;
        }
    }
}

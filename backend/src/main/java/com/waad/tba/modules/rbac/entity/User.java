package com.waad.tba.modules.rbac.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq_gen")
    @SequenceGenerator(name = "user_seq_gen", sequenceName = "user_seq", allocationSize = 50)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    /**
     * The user's role in the system. Maps directly to SystemRole enum.
     * Valid values: SUPER_ADMIN, MEDICAL_REVIEWER, MEDICAL_REVIEW_HEAD, INSURANCE_MANAGER, ACCOUNTANT, PROVIDER_STAFF,
     *              EMPLOYER_ADMIN, DATA_ENTRY, FINANCE_VIEWER
     */
    @Column(name = "user_type", nullable = false)
    @Builder.Default
    private String userType = "DATA_ENTRY";

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    /**
     * Employer ID — for EMPLOYER_ADMIN users.
     * Restricts access to employer-specific data.
     */
    @Column(name = "employer_id")
    private Long employerId;

    /**
     * Provider ID — for PROVIDER_STAFF users.
     * Restricts access to provider-specific data (visits, claims).
     */
    @Column(name = "provider_id")
    private Long providerId;

    // ========================================================================
    // EMPLOYER-SPECIFIC PERMISSIONS (fine-grained toggles for employer users)
    // ========================================================================

    @Column(name = "can_view_claims")
    @Builder.Default
    private Boolean canViewClaims = true;

    @Column(name = "can_view_visits")
    @Builder.Default
    private Boolean canViewVisits = true;

    @Column(name = "can_view_reports")
    @Builder.Default
    private Boolean canViewReports = true;

    @Column(name = "can_view_members")
    @Builder.Default
    private Boolean canViewMembers = true;

    @Column(name = "can_view_benefit_policies")
    @Builder.Default
    private Boolean canViewBenefitPolicies = true;

    // ========================================================================
    // SECURITY FIELDS
    // ========================================================================

    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private Integer failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // ========================================================================
    // ROLE HELPER
    // ========================================================================

    /**
     * Check if this user has the SUPER_ADMIN role.
     */
    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(userType);
    }

    // ========================================================================
    // ACCOUNT LOCKOUT METHODS
    // ========================================================================

    public boolean isLocked() {
        if (lockedUntil == null) return false;
        return LocalDateTime.now().isBefore(lockedUntil);
    }

    public void lockAccount() {
        this.lockedUntil = LocalDateTime.now().plusMinutes(30);
    }

    public void unlockAccount() {
        this.lockedUntil = null;
        this.failedLoginCount = 0;
    }

    public void incrementFailedLoginCount() {
        this.failedLoginCount++;
        if (this.failedLoginCount >= 5) {
            lockAccount();
        }
    }

    public void resetFailedLoginCount() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
}

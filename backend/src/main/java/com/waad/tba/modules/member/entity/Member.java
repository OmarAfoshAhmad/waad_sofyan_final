package com.waad.tba.modules.member.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.employer.entity.Employer;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "members", uniqueConstraints = {
        @UniqueConstraint(columnNames = "cardNumber", name = "uk_member_card_number"),
        @UniqueConstraint(columnNames = "barcode", name = "uk_member_barcode")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    // Self-referencing principal/dependent structure (parent=null → principal)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Member parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Member> dependents = new ArrayList<>();

    /** Relationship type — only for dependents */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, name = "relationship")
    private Relationship relationship;

    /** Auto-calculated: PRINCIPAL if parent==null, DEPENDENT if parent!=null */
    @Transient
    public MemberType getType() {
        return parent == null ? MemberType.PRINCIPAL : MemberType.DEPENDENT;
    }

    @Transient
    public boolean isPrincipal() {
        return parent == null;
    }

    @Transient
    public boolean isDependent() {
        return parent != null;
    }

    @Transient
    public int getDependentsCount() {
        return (dependents != null) ? dependents.size() : 0;
    }

    // ==================== EMPLOYER ====================

    @NotNull(message = "Employer is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;

    /** Auto-assigned from employer's active policy */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benefit_policy_id")
    private BenefitPolicy benefitPolicy;

    @NotBlank(message = "Full name is required")
    @Column(nullable = false, length = 200, name = "full_name")
    private String fullName;

    /** @deprecated Use nationalNumber instead */
    @Deprecated
    @Column(length = 50, name = "civil_id")
    private String civilId;

    @Column(length = 50, name = "national_number")
    private String nationalNumber;

    // Card number: principal gets base, dependent gets base+suffix (e.g.
    // "123456-01")
    @Column(length = 50, name = "card_number")
    private String cardNumber;

    // Barcode: auto-generated for principals only (WAD-YYYY-NNNNNNNN)
    @Column(unique = true, length = 100, name = "barcode")
    private String barcode;

    // Date of Birth - OPTIONAL
    @Column(name = "birth_date")
    private LocalDate birthDate;

    // Gender - OPTIONAL (DB constraint allows MALE/FEMALE only)
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @PrePersist
    @PreUpdate
    public void validateState() {
        if (this.gender == Gender.UNDEFINED) {
            this.gender = null;
        }

        // UNIFIED VALIDATION: Barcode MUST exist for PRINCIPAL only
        if (isPrincipal()) {
            if (this.barcode == null || this.barcode.trim().isEmpty()) {
                throw new IllegalStateException(
                        "Principal Member cannot be persisted without a valid barcode. " +
                                "Use BarcodeGeneratorService.generateForMember().");
            }
        } else if (isDependent()) {
            // IMPORTANT: Dependents should NOT have barcode
            if (this.barcode != null && !this.barcode.trim().isEmpty()) {
                throw new IllegalStateException(
                        "Dependent Member should NOT have a barcode. " +
                                "Barcode is only for Principal members.");
            }
            // Dependents MUST have a parent
            if (this.parent == null) {
                throw new IllegalStateException(
                        "Dependent Member must have a parent (Principal Member).");
            }
            // Dependents MUST have a relationship
            if (this.relationship == null) {
                throw new IllegalStateException(
                        "Dependent Member must have a relationship type (e.g., SON, DAUGHTER, WIFE).");
            }
        }

    }

    @Transient
    public Member getPrincipalMember() {
        return isPrincipal() ? this : parent;
    }

    @Transient
    public String getFamilyBarcode() {
        return getPrincipalMember().getBarcode();
    }

    @Enumerated(EnumType.STRING)
    @Column(length = 20, name = "marital_status")
    private MaritalStatus maritalStatus;

    @Column(length = 20)
    private String phone;

    @Email(message = "Invalid email format")
    @Column(length = 255)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String nationality;

    // Insurance Information
    @Column(length = 100, name = "policy_number")
    private String policyNumber;

    // Employment Information
    @Column(length = 100, name = "employee_number")
    private String employeeNumber;

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Column(length = 100)
    private String occupation;

    // Membership Status
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20, name = "card_status")
    private CardStatus cardStatus = CardStatus.ACTIVE;

    @Column(length = 500, name = "blocked_reason")
    private String blockedReason;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    // Eligibility
    @Builder.Default
    @Column(nullable = false, name = "eligibility_status")
    private Boolean eligibilityStatus = true;

    @Column(name = "eligibility_updated_at")
    private LocalDateTime eligibilityUpdatedAt;

    // Additional Information
    @Column(length = 500, name = "photo_url")
    private String photoUrl;

    @Column(length = 500, name = "profile_photo_path")
    private String profilePhotoPath;

    @Column(length = 2000)
    private String notes;

    // Enterprise Smart Card Fields
    @Column(name = "card_activated_at")
    private LocalDateTime cardActivatedAt;

    @Column(name = "is_smart_card")
    private Boolean isSmartCard;

    @Column(name = "is_vip")
    private Boolean isVip;

    @Column(name = "is_urgent")
    private Boolean isUrgent;

    @Column(length = 1000, name = "emergency_notes")
    private String emergencyNotes;

    // Flexible Attributes
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MemberAttribute> attributes = new ArrayList<>();

    // Audit fields
    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @CreatedDate
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    public boolean hasActiveBenefitPolicy() {
        return benefitPolicy != null && benefitPolicy.isEffective();
    }

    @Transient
    public boolean hasActiveBenefitPolicyOn(LocalDate date) {
        return benefitPolicy != null && benefitPolicy.isEffectiveOn(date);
    }

    // ==================== ENUMS ====================

    public enum MemberType {
        PRINCIPAL, // Parent = null, has barcode, can have dependents
        DEPENDENT // Parent != null, no barcode, has relationship
    }

    /** Card-number suffix code per relationship (e.g. DAUGHTER("D") → "D1","D2") */
    public enum Relationship {
        WIFE("W"), // زوجة
        HUSBAND("H"), // زوج
        SON("S"), // ابن
        DAUGHTER("D"), // ابنة
        FATHER("F"), // أب
        MOTHER("M"), // أم
        BROTHER("B"), // أخ
        SISTER("SR"); // أخت

        private final String cardCode;

        Relationship(String cardCode) {
            this.cardCode = cardCode;
        }

        public String getCardCode() {
            return cardCode;
        }
    }

    public enum Gender {
        MALE, FEMALE, UNDEFINED
    }

    public enum MaritalStatus {
        SINGLE, MARRIED, DIVORCED, WIDOWED
    }

    public enum MemberStatus {
        ACTIVE, SUSPENDED, TERMINATED, PENDING
    }

    public enum CardStatus {
        ACTIVE, INACTIVE, BLOCKED, EXPIRED
    }
}

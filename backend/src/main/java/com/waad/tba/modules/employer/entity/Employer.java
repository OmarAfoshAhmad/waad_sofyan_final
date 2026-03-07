package com.waad.tba.modules.employer.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * Employer Entity - The ONLY top-level business entity in the system.
 * 
 * All members, benefit policies, provider contracts, claims, and settlements
 * belong to an employer. This is the single source of truth for employer data.
 * 
 * Business Rules:
 * - Each employer must have a unique code
 * - Employer name is required
 * - Employers can be marked as active/inactive
 * 
 * Domain Architecture Decision (2026-02-13):
 * - NO Insurance Company entity
 * - NO TPA entity
 * - NO Organization type hierarchy
 * - Employer is the singular business entity
 */
@Entity
@Table(name = "employers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Employer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Employer code is required")
    @Column(nullable = false, unique = true)
    private String code;

    @NotBlank(message = "Employer name is required")
    @Column(nullable = false, name = "name")
    private String name;
    
    private String address;

    private String phone;

    @Email(message = "Email must be valid")
    private String email;

    /**
     * URL to the employer's logo image
     * Used for PDF reports and UI branding
     */
    private String logoUrl;

    /**
     * Type of business (e.g., "Healthcare", "Insurance", "Manufacturing")
     * Displayed on employer reports and documents
     */
    private String businessType;

    /**
     * Employer's website URL
     * Displayed on employer reports and documents
     */
    private String website;

    /**
     * Commercial Registration Number (رقم السجل التجاري)
     * Required for financial reports and legal compliance
     */
    @Column(name = "cr_number")
    private String crNumber;

    /**
     * Tax/VAT Number (الرقم الضريبي)
     * Required for invoicing and financial reports
     */
    @Column(name = "tax_number")
    private String taxNumber;

    /**
     * Contract start date with the employer (تاريخ بداية التعاقد)
     */
    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    /**
     * Contract end date with the employer (تاريخ انتهاء التعاقد)
     */
    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    /**
     * Maximum number of members allowed for this employer (الحد الأقصى للأعضاء)
     * NULL means unlimited
     */
    @Column(name = "max_member_limit")
    private Integer maxMemberLimit;

    @Builder.Default
    private Boolean active = true;

    /**
     * Marks this employer as the default/primary employer
     * Used by system to determine the default context when no employer is specified
     */
    @Builder.Default
    private Boolean isDefault = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

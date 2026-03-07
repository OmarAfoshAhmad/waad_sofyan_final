package com.waad.tba.modules.employer.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Employer Response DTO - Unified Version
 * 
 * Field Mapping:
 * - Returns unified 'name' field
 * - Returns 'code' (canonical backend name)
 * - Includes audit timestamps
 * 
 * @see EMPLOYER_API_CONTRACT.md
 */
@Data
@Builder
public class EmployerResponseDto {

    /**
     * Employer ID (Primary Key)
     */
    private Long id;

    /**
     * Employer code (canonical backend name)
     */
    private String code;

    /**
     * Employer name
     */
    private String name;

    /**
     * Active status
     */
    private boolean active;

    /**
     * Default employer status
     */
    private boolean isDefault;

    /**
     * Archived status (soft delete)
     * Archived employers are hidden from default lists
     */
    private boolean archived;

    /**
     * Logo URL
     */
    private String logoUrl;

    /**
     * Business type
     */
    private String businessType;

    /**
     * Website
     */
    private String website;

    /** Contact phone */
    private String phone;

    /** Contact email */
    private String email;

    /** Address */
    private String address;

    /** Commercial Registration Number */
    private String crNumber;

    /** Tax/VAT Number */
    private String taxNumber;

    /** Contract start date */
    private LocalDate contractStartDate;

    /** Contract end date */
    private LocalDate contractEndDate;

    /** Maximum members allowed (null = unlimited) */
    private Integer maxMemberLimit;

    /**
     * Creation timestamp (audit field)
     */
    private LocalDateTime createdAt;

    /**
     * Last update timestamp (audit field)
     */
    private LocalDateTime updatedAt;
}

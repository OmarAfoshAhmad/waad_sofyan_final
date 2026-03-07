package com.waad.tba.modules.employer.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Employer Update DTO - Unified Version
 * 
 * Field Normalization:
 * - Accepts both 'code' and 'employerCode' (via @JsonAlias)
 * - Uses unified 'name' field
 * 
 * @see EMPLOYER_API_CONTRACT.md
 */
@Data
public class EmployerUpdateDto {

    /**
     * Employer code - REQUIRED for update
     * Note: Auto-generated codes should not be changed
     * Accepts: 'code' or 'employerCode' (frontend compatibility)
     */
    @NotBlank(message = "Employer code is required")
    @JsonAlias({"employerCode"})
    @Size(max = 50, message = "Employer code too long")
    private String code;

    /**
     * Employer name - REQUIRED
     */
    @NotBlank(message = "Employer name is required")
    @Size(max = 200, message = "Employer name too long")
    private String name;
    
    /**
     * Active status - OPTIONAL
     */
    private Boolean active;

    @Size(max = 255, message = "Address too long")
    private String address;

    @Pattern(regexp = "^\\+?[\\d\\s\\-\\(\\)]{7,25}$", message = "Invalid phone number format")
    @Size(max = 30, message = "Phone too long")
    private String phone;

    @Email(message = "Invalid email format")
    @Size(max = 150, message = "Email too long")
    private String email;

    @Size(max = 200, message = "Business type too long")
    private String businessType;

    private String website;
    private String logoUrl;

    /** Commercial Registration Number (رقم السجل التجاري) */
    @Size(max = 50, message = "CR number too long")
    private String crNumber;

    /** Tax/VAT Number (الرقم الضريبي) */
    @Size(max = 50, message = "Tax number too long")
    private String taxNumber;

    /** Contract start date with this employer */
    private LocalDate contractStartDate;

    /** Contract end date with this employer */
    private LocalDate contractEndDate;

    /** Maximum members allowed (null = unlimited) */
    @Positive(message = "Max member limit must be positive")
    private Integer maxMemberLimit;
}

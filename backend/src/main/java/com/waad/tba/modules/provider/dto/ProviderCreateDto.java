package com.waad.tba.modules.provider.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCreateDto {

    /** Provider name (required) */
    @NotBlank(message = "Provider name is required")
    @Size(max = 255, message = "Provider name must not exceed 255 characters")
    private String name;

    @Size(max = 100, message = "License number must not exceed 100 characters")
    private String licenseNumber;

    @Size(max = 50, message = "Tax number must not exceed 50 characters")
    private String taxNumber;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @Pattern(regexp = "^[+\\d][\\d\\s\\-]{5,19}$", message = "Invalid phone number format")
    private String phone;

    @Email(message = "Invalid email format")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    /** Accepted values: CLINIC, HOSPITAL, PHARMACY, LAB, RADIOLOGY, OTHER */
    @Size(max = 50, message = "Provider type must not exceed 50 characters")
    private String providerType;

    /** Accepted values: IN_NETWORK, OUT_OF_NETWORK, PREFERRED */
    @Size(max = 50, message = "Network status must not exceed 50 characters")
    private String networkStatus;

    private LocalDate contractStartDate;
    private LocalDate contractEndDate;

    /**
     * PHASE 3 REVIEW: defaultDiscountRate removed from DTO.
     * Use ProviderContract.discountPercent instead for all new contracts.
     */
    // private BigDecimal defaultDiscountRate; // DEPRECATED - DO NOT USE

    private Boolean allowAllEmployers;
}

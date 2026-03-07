package com.waad.tba.modules.provider.dto;

import jakarta.validation.constraints.Email;
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
public class ProviderUpdateDto {

    /** Provider name — optional for partial update */
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

    @Size(max = 50, message = "Provider type must not exceed 50 characters")
    private String providerType;

    @Size(max = 50, message = "Network status must not exceed 50 characters")
    private String networkStatus;

    private LocalDate contractStartDate;
    private LocalDate contractEndDate;

    /**
     * PHASE 3 REVIEW: defaultDiscountRate removed from DTO.
     * Use ProviderContract.discountPercent instead for all new contracts.
     */
    // private BigDecimal defaultDiscountRate; // DEPRECATED - DO NOT USE

    private Boolean active;

    /**
     * If true, provider services are available to ALL employers.
     * If false, restricted to allowedEmployers list.
     */
    private Boolean allowAllEmployers;
}

package com.waad.tba.modules.medicaltaxonomy.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for creating a new Medical Service.
 * 
 * Field Mapping:
 * - Frontend: serviceCode → Backend: code
 * - Frontend: name → Backend: name (unified)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalServiceCreateDto {

    /**
     * Unique service code (immutable)
     * Examples: "SRV-CARDIO-001", "SRV-LAB-CBC"
     */
    @NotBlank(message = "Service code is required")
    @Size(max = 50, message = "Service code must not exceed 50 characters")
    @JsonAlias({ "serviceCode", "code" })
    private String code;

    /**
     * Service name (unified field)
     */
    @NotBlank(message = "Service name is required")
    @Size(max = 200, message = "Service name must not exceed 200 characters")
    private String name;

    /**
     * Category ID (required)
     */
    @NotNull(message = "Category ID is required")
    @Positive(message = "Category ID must be positive")
    private Long categoryId;

    /**
     * Specialty ID (optional — auto-derived from category when null)
     * When provided, must belong to the specified category.
     */
    @Positive(message = "Specialty ID must be positive")
    private Long specialtyId;

    /**
     * Service description (optional)
     */
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /**
     * Base price (reference only - NOT for final calculation)
     */
    @DecimalMin(value = "0.00", message = "Base price must be >= 0")
    private BigDecimal basePrice;

    /**
     * Flag indicating if pre-authorization is required
     */
    @JsonAlias({ "requiresPreApproval", "requiresPA" })
    @Builder.Default
    private Boolean requiresPA = false;

    /**
     * Active status (defaults to true)
     */
    @Builder.Default
    private Boolean active = true;
}

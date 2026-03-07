package com.waad.tba.modules.visit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.waad.tba.modules.visit.entity.VisitType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitCreateDto {

    @NotNull(message = "Member ID is required")
    @Positive(message = "Member ID must be positive")
    private Long memberId;

    /**
     * Provider ID - REQUIRED for all visits.
     * - For PROVIDER users: auto-filled from session (no need to send)
     * - For ADMIN users: must be explicitly provided
     *
     * NOTE: @NotNull removed because PROVIDER users get it auto-filled from
     * session.
     * Service layer validates that ADMIN users provide it.
     */
    @Positive(message = "Provider ID must be positive")
    private Long providerId;

    @NotNull(message = "Visit date is required")
    @PastOrPresent(message = "Visit date cannot be in the future")
    private LocalDate visitDate;

    @NotBlank(message = "Doctor name is required")
    @Size(max = 255, message = "Doctor name must not exceed 255 characters")
    private String doctorName;

    @Size(max = 100, message = "Specialty must not exceed 100 characters")
    private String specialty;

    @Size(max = 500, message = "Diagnosis must not exceed 500 characters")
    private String diagnosis;

    @Size(max = 1000, message = "Treatment must not exceed 1000 characters")
    private String treatment;

    @DecimalMin(value = "0.00", message = "Total amount must be >= 0")
    private BigDecimal totalAmount;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    /**
     * Type of visit/service location.
     * Optional — defaults to OUTPATIENT if not provided.
     */
    private VisitType visitType;
}

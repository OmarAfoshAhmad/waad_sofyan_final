package com.waad.tba.modules.preauthorization.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for updating pre-authorization DATA fields only.
 * 
 * SECURITY: This DTO can ONLY be used by PROVIDER and EMPLOYER_ADMIN roles.
 * Allowed ONLY when status is PENDING or NEEDS_CORRECTION.
 * 
 * @since Provider Portal Security Fix (Phase 3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthDataUpdateDto {
    
    /**
     * Expected service date - can be updated before approval
     */
    private LocalDate expectedServiceDate;
    
    /**
     * Clinical justification for the pre-authorization request
     */
    @Size(max = 2000, message = "Clinical justification must not exceed 2000 characters")
    private String clinicalJustification;
    
    /**
     * Additional notes
     */
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
    
    /**
     * Priority level (if applicable)
     */
    private String priority;
}

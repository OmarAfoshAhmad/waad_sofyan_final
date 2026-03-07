package com.waad.tba.modules.preauthorization.dto;

import jakarta.validation.constraints.*;
import lombok.*;


/**
 * DTO for updating a PreAuthorization (CANONICAL REBUILD)
 * 
 * Note: In canonical architecture, price cannot be changed (comes from contract).
 * Only diagnosis, notes, priority can be updated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationUpdateDto {

    private String priority; // EMERGENCY, URGENT, NORMAL, LOW

    @Size(max = 20, message = "Diagnosis code must not exceed 20 characters")
    private String diagnosisCode;
    
    @Size(max = 500, message = "Diagnosis description must not exceed 500 characters")
    private String diagnosisDescription;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    @Positive(message = "Expiry days must be positive")
    private Integer expiryDays;
}

package com.waad.tba.modules.preauthorization.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for approving a PreAuthorization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationApproveDto {

    /**
     * Approved amount (for APPROVED status)
     * Optional - pre-authorizations no longer have strict financial matters
     */
    private BigDecimal approvedAmount;

    @DecimalMin(value = "0.0", message = "Copay percentage must be 0 or greater")
    @DecimalMax(value = "100.0", message = "Copay percentage must not exceed 100")
    @Digits(integer = 3, fraction = 2, message = "Copay percentage must have at most 3 integer digits and 2 decimal places")
    private BigDecimal copayPercentage;

    @Size(max = 1000, message = "Approval notes must not exceed 1000 characters")
    private String approvalNotes;
}

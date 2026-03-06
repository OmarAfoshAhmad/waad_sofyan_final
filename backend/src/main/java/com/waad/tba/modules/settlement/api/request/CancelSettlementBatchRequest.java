package com.waad.tba.modules.settlement.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║            CANCEL SETTLEMENT BATCH REQUEST - API CONTRACT v1                  ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 * 
 * PUBLIC API CONTRACT for cancelling a settlement batch.
 * 
 * BUSINESS RULES (Backend enforced):
 * ✓ Can cancel DRAFT or CONFIRMED batches
 * ✓ CANNOT cancel PAID batches (use reversal instead)
 * ✓ All claims returned to APPROVED status
 * ✓ Batch items deleted
 * ✓ Cancellation reason required for audit trail
 * 
 * @since API v1
 * @see SETTLEMENT_API_CONTRACT.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to cancel a settlement batch")
public class CancelSettlementBatchRequest {

    /**
     * Reason for cancellation (required for audit trail).
     * REQUIRED - Minimum 10 characters, maximum 500 characters.
     */
    @NotBlank(message = "Cancellation reason is required")
    @Size(min = 10, max = 500, message = "Cancellation reason must be between 10 and 500 characters")
    @Schema(
        description = "Reason for cancelling the batch (audit trail)",
        example = "Provider requested to revise claim amounts before settlement",
        requiredMode = Schema.RequiredMode.REQUIRED,
        minLength = 10,
        maxLength = 500
    )
    private String cancellationReason;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EXPLICITLY FORBIDDEN FIELDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    // ❌ NO status - set by backend
    // ❌ NO cancelledBy - extracted from JWT
    // ❌ NO cancelledAt - set by backend
    // ❌ NO claimIds - fetched from batch items
}

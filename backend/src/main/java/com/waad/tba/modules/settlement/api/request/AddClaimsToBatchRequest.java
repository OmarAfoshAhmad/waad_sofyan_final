package com.waad.tba.modules.settlement.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║              ADD CLAIMS TO BATCH REQUEST - API CONTRACT v1                    ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 * 
 * PUBLIC API CONTRACT for adding claims to a DRAFT settlement batch.
 * 
 * BUSINESS RULES (Backend enforced):
 * ✓ Batch must be in DRAFT status
 * ✓ Claims must be APPROVED
 * ✓ Claims must belong to same provider as batch
 * ✓ Claims cannot be in another batch
 * ✓ Batch totals recalculated automatically
 * 
 * @since API v1
 * @see SETTLEMENT_API_CONTRACT.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to add claims to a settlement batch")
public class AddClaimsToBatchRequest {

    /**
     * List of claim IDs to add to the batch.
     * REQUIRED - Must contain at least one claim ID.
     */
    @NotNull(message = "Claim IDs list is required")
    @NotEmpty(message = "Must provide at least one claim ID")
    @Schema(
        description = "List of claim IDs to add to the batch",
        example = "[789, 790, 791]",
        requiredMode = Schema.RequiredMode.REQUIRED,
        minLength = 1
    )
    private List<Long> claimIds;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EXPLICITLY FORBIDDEN FIELDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    // ❌ NO amounts - calculated from database
    // ❌ NO claim details - fetched from database
    // ❌ NO validation flags - backend validates
}

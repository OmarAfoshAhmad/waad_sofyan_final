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
 * ║           REMOVE CLAIMS FROM BATCH REQUEST - API CONTRACT v1                  ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 * 
 * PUBLIC API CONTRACT for removing claims from a DRAFT settlement batch.
 * 
 * BUSINESS RULES (Backend enforced):
 * ✓ Batch must be in DRAFT status
 * ✓ Claims must be in the specified batch
 * ✓ Claims returned to APPROVED status
 * ✓ Batch totals recalculated automatically
 * 
 * @since API v1
 * @see SETTLEMENT_API_CONTRACT.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to remove claims from a settlement batch")
public class RemoveClaimsFromBatchRequest {

    /**
     * List of claim IDs to remove from the batch.
     * REQUIRED - Must contain at least one claim ID.
     */
    @NotNull(message = "Claim IDs list is required")
    @NotEmpty(message = "Must provide at least one claim ID")
    @Schema(
        description = "List of claim IDs to remove from the batch",
        example = "[789, 790]",
        requiredMode = Schema.RequiredMode.REQUIRED,
        minLength = 1
    )
    private List<Long> claimIds;
}

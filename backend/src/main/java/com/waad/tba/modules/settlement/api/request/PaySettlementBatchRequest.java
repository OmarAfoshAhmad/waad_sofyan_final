package com.waad.tba.modules.settlement.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║              PAY SETTLEMENT BATCH REQUEST - API CONTRACT v1                   ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 * 
 * PUBLIC API CONTRACT for paying a settlement batch (CONFIRMED → PAID).
 * 
 * ⚠️ CRITICAL FINANCIAL OPERATION ⚠️
 * 
 * FINANCIAL SAFETY RULES:
 * ✓ Frontend NEVER sends payment amount
 * ✓ Amount taken from immutable batch totals
 * ✓ Backend creates DEBIT transaction
 * ✓ Provider account balance reduced
 * ✓ All claims marked SETTLED
 * ✓ Operation is IRREVERSIBLE
 * 
 * BUSINESS RULES (Backend enforced):
 * ✓ Batch must be in CONFIRMED status
 * ✓ Provider account must have sufficient balance
 * ✓ Payment reference must be unique
 * ✓ All claims transitioned to SETTLED atomically
 * 
 * @since API v1
 * @see SETTLEMENT_API_CONTRACT.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to pay a settlement batch (IRREVERSIBLE)")
public class PaySettlementBatchRequest {

    /**
     * Payment reference number (bank transfer ID, check number, etc.).
     * REQUIRED - Used for audit trail and reconciliation.
     * Must be unique across all payments.
     */
    @NotBlank(message = "Payment reference is required")
    @Size(min = 3, max = 100, message = "Payment reference must be between 3 and 100 characters")
    @Schema(
        description = "Unique payment reference number for audit trail",
        example = "TRF-2026-001234",
        requiredMode = Schema.RequiredMode.REQUIRED,
        minLength = 3,
        maxLength = 100
    )
    private String paymentReference;
    
    /**
     * Payment method used for the settlement.
     * REQUIRED - Must be one of: BANK_TRANSFER, CHECK, CASH.
     */
    @NotNull(message = "Payment method is required")
    @Pattern(
        regexp = "BANK_TRANSFER|CHECK|CASH",
        message = "Payment method must be one of: BANK_TRANSFER, CHECK, CASH"
    )
    @Schema(
        description = "Payment method used for settlement",
        example = "BANK_TRANSFER",
        requiredMode = Schema.RequiredMode.REQUIRED,
        allowableValues = {"BANK_TRANSFER", "CHECK", "CASH"}
    )
    private String paymentMethod;
    
    /**
     * Optional bank account number (for BANK_TRANSFER).
     * Maximum 50 characters.
     */
    @Size(max = 50, message = "Bank account number cannot exceed 50 characters")
    @Schema(
        description = "Bank account number used for payment (optional)",
        example = "SA1234567890123456789012",
        maxLength = 50
    )
    private String bankAccountNumber;
    
    /**
     * Optional payment notes.
     * Maximum 500 characters.
     */
    @Size(max = 500, message = "Payment notes cannot exceed 500 characters")
    @Schema(
        description = "Optional payment notes",
        example = "Batch payment processed via NCB transfer",
        maxLength = 500
    )
    private String paymentNotes;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EXPLICITLY FORBIDDEN FIELDS (CRITICAL FINANCIAL SAFETY)
    // ═══════════════════════════════════════════════════════════════════════════
    
    // ❌ NO paymentAmount - CALCULATED from batch totals (IMMUTABLE)
    // ❌ NO totalAmount - fetched from database
    // ❌ NO netAmount - fetched from database
    // ❌ NO deductions - fetched from database
    // ❌ NO claimIds - fetched from batch items
    // ❌ NO status - set by backend
    // ❌ NO paidBy - extracted from JWT
    // ❌ NO paidAt - set by backend
    // ❌ NO accountBalance - calculated by backend
    
    /**
     * ╔═══════════════════════════════════════════════════════════════════════════╗
     * ║                        FINANCIAL INTEGRITY GUARANTEE                      ║
     * ╚═══════════════════════════════════════════════════════════════════════════╝
     * 
     * This contract GUARANTEES that:
     * 
     * 1. Frontend CANNOT modify payment amounts
     * 2. Backend is SOLE source of truth for financial values
     * 3. Payment amount = batch.totalNetAmount (immutable since CONFIRMED)
     * 4. Provider account DEBIT = exactly batch.totalNetAmount
     * 5. All claims settled for their approved amounts only
     * 6. No arithmetic manipulation possible by client
     * 7. Audit trail complete with payment reference
     * 8. Operation atomic - all claims settled or none
     */
}

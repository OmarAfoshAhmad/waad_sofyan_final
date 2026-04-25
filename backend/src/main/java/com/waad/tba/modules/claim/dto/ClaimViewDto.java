package com.waad.tba.modules.claim.dto;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimViewDto {
    private Long id;

    /**
     * Claim Number (formatted reference: CLM-YYYYMMDD-XXXX or just ID)
     */
    private String claimNumber;

    // Member information
    private Long memberId;
    private String memberFullName;
    /**
     * Alias for memberFullName (Frontend compatibility)
     */
    private String memberName;
    private String memberNationalNumber;

    // Employer information (جهة العمل)
    private Long employerId;
    private String employerName;
    private String employerCode;

    // Insurance Company information
    private String insuranceCompanyName;
    private String insuranceCompanyCode;

    // Benefit Package information
    private Long benefitPackageId;
    private String benefitPackageName;
    private String benefitPackageCode;

    // Pre-Approval information
    private Long preApprovalId;
    private String preApprovalReferenceNumber;
    private String preApprovalStatus;

    // ═══════════════════════════════════════════════════════════════════════════
    // VISIT-CENTRIC ARCHITECTURE (2026-01-15)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Visit ID - Link to the Visit entity (MANDATORY)
     */
    private Long visitId;

    /**
     * Visit Type (EMERGENCY, OUTPATIENT, etc.)
     */
    private String visitType;

    /**
     * Service Date (from Visit)
     */
    private LocalDate serviceDate;

    // ═══════════════════════════════════════════════════════════════════════════
    // DIAGNOSIS (System-Selected, not free-text)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Diagnosis Code (ICD-10 or local code)
     */
    private String diagnosisCode;

    /**
     * Diagnosis Description
     */
    private String diagnosisDescription;

    /**
     * Patient complaint
     */
    private String complaint;

    // Claim details
    private Long providerId;
    private String providerName;
    private String doctorName;

    /**
     * @deprecated Use diagnosisCode and diagnosisDescription instead
     */
    @Deprecated
    private String diagnosis;

    private LocalDate visitDate;

    // Financial information (Basic)
    private BigDecimal requestedAmount;
    /**
     * Alias for requestedAmount (Frontend compatibility)
     */
    private BigDecimal totalAmount;
    private BigDecimal approvedAmount;
    private BigDecimal refusedAmount;
    private BigDecimal differenceAmount;

    // ========== Provider Contract Fields ==========

    /**
     * نسبة الخصم بعقد مقدم الخدمة
     */
    private BigDecimal providerDiscountPercent;

    /**
     * قيمة الخصم بناءً على النسبة
     */
    private BigDecimal providerDiscountAmount;

    // ========== Financial Snapshot (MVP Phase) ==========

    /**
     * نسبة تحمل المريض (مجموع الخصومات + Co-Pay)
     */
    private BigDecimal patientCoPay;

    /**
     * المبلغ الصافي المستحق لمقدم الخدمة
     */
    private BigDecimal netProviderAmount;

    /**
     * نسبة المشاركة في التكلفة (%)
     */
    private BigDecimal coPayPercent;

    /**
     * الخصم المُطبق (Deductible)
     */
    private BigDecimal deductibleApplied;

    // ========== Settlement Fields (MVP Phase) ==========

    /**
     * رقم مرجع الدفع
     */
    private String paymentReference;

    /**
     * تاريخ التسوية
     */
    private LocalDateTime settledAt;

    /**
     * ملاحظات التسوية
     */
    private String settlementNotes;

    // ========== SLA Fields ==========

    /**
     * تاريخ الإنجاز المتوقع (يُحسب تلقائياً عند التقديم)
     */
    private LocalDate expectedCompletionDate;

    /**
     * تاريخ الإنجاز الفعلي (يُسجل عند الموافقة/الرفض)
     */
    private LocalDate actualCompletionDate;

    /**
     * هل تمت المعالجة ضمن الـ SLA؟
     */
    private Boolean withinSla;

    /**
     * عدد أيام العمل المستغرقة
     */
    private Integer businessDaysTaken;

    /**
     * أيام SLA المحددة وقت التقديم
     */
    private Integer slaDaysConfigured;

    /**
     * حالة SLA (للعرض في الواجهة)
     * ON_TRACK: ضمن المسار
     * AT_RISK: في خطر
     * BREACHED: تجاوز
     * MET: تم الالتزام
     */
    private String slaStatus;

    // Status and review
    private ClaimStatus status;
    private String statusLabel;
    private String reviewerComment;
    private LocalDateTime reviewedAt;

    /**
     * Allowed next statuses for this claim (Backend-Driven Workflow).
     * Frontend MUST use this to determine available actions.
     * Empty list = terminal state or no permissions.
     */
    private java.util.Set<ClaimStatus> allowedNextStatuses;

    /**
     * Whether the claim can be edited in current status.
     * Only DRAFT and RETURNED_FOR_INFO allow edits.
     */
    private Boolean canEdit;

    // Counts
    private Integer serviceCount;
    private Integer attachmentsCount;

    // Lines and attachments
    private List<ClaimLineDto> lines;
    private List<ClaimAttachmentDto> attachments;

    // Audit fields
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    // Soft delete fields
    private LocalDateTime deletedAt;
    private String deletedBy;
    private String voidReason;

    // ========== Helper Methods ==========

    /**
     * Get financial summary in Arabic
     */
    public String getFinancialSummaryArabic() {
        if (requestedAmount == null)
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append("💰 المطلوب: ").append(requestedAmount).append(" د.ل");

        if (patientCoPay != null) {
            sb.append(" | 👤 تحمل المريض: ").append(patientCoPay).append(" د.ل");
        }

        if (netProviderAmount != null) {
            sb.append(" | 🏥 المستحق: ").append(netProviderAmount).append(" د.ل");
        }

        return sb.toString();
    }

    /**
     * Check if claim has financial snapshot calculated
     */
    public boolean hasFinancialSnapshot() {
        return patientCoPay != null && netProviderAmount != null;
    }

    // ==================== COVERAGE CONTEXT ====================
    private Boolean manualCategoryEnabled;
    private String primaryCategoryCode;
    private String primaryCategoryName;
    private Boolean fullCoverage;

    // Monthly Batch Information
    private Long claimBatchId;
    private String claimBatchCode;
}

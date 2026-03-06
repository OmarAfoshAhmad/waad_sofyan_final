package com.waad.tba.modules.claim.api;

import com.waad.tba.modules.claim.api.request.*;
import com.waad.tba.modules.claim.api.response.ClaimListResponse;
import com.waad.tba.modules.claim.api.response.ClaimResponse;
import com.waad.tba.modules.claim.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Mapper: Convert between API Contracts and Internal DTOs
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * RESPONSIBILITY:
 * This mapper converts between:
 * 1. API v1 Request Contracts → Internal DTOs (for service layer)
 * 2. Internal DTOs → API v1 Response Contracts (for controller)
 * 
 * SEPARATION OF CONCERNS:
 * - API contracts (api/) = Public interface for frontend
 * - Internal DTOs (dto/) = Service layer data transfer
 * - Entities (entity/) = Database representation
 * 
 * This separation allows:
 * - API versioning without breaking service layer
 * - Internal DTO changes without breaking API contracts
 * - Clear contract boundaries
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Component
public class ClaimApiMapper {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // REQUEST CONTRACTS → INTERNAL DTOs (Inbound)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Convert CreateClaimRequest (API v1) to ClaimCreateDto (internal)
     */
    public ClaimCreateDto toCreateDto(CreateClaimRequest request) {
        return ClaimCreateDto.builder()
                .visitId(request.getVisitId())
                .memberId(request.getMemberId())
                .providerId(request.getProviderId())
                .diagnosisCode(request.getDiagnosisCode())
                .diagnosisDescription(request.getDiagnosisDescription())
                .preAuthorizationId(request.getPreAuthorizationId())
                .doctorName(request.getDoctorName())
                .serviceDate(request.getServiceDate())
                .notes(request.getNotes())
                .lines(request.getLines().stream()
                        .map(this::toClaimLineDto)
                        .collect(Collectors.toList()))
                .build();
    }
    
    /**
     * Convert CreateClaimRequest.ClaimLineRequest to ClaimLineDto
     */
    private ClaimLineDto toClaimLineDto(CreateClaimRequest.ClaimLineRequest lineRequest) {
        return ClaimLineDto.builder()
                .medicalServiceId(lineRequest.getMedicalServiceId())
                .serviceCategoryId(lineRequest.getServiceCategoryId())
                .serviceCategoryName(lineRequest.getServiceCategoryName())
                .quantity(lineRequest.getQuantity())
                // ✅ NO price fields - calculated by backend
                // ✅ NO notes field - ClaimLineDto doesn't have notes
                .build();
    }
    
    /**
     * Convert UpdateClaimRequest (API v1) to ClaimUpdateDto (internal)
     */
    public ClaimUpdateDto toUpdateDto(UpdateClaimRequest request) {
        return ClaimUpdateDto.builder()
                .doctorName(request.getDoctorName())
                .diagnosisCode(request.getDiagnosisCode())
                .diagnosisDescription(request.getDiagnosisDescription())
                .preAuthorizationId(request.getPreAuthorizationId())
                // notes field would need to be added to ClaimUpdateDto if needed
                // ✅ NO amount fields - forbidden
                .build();
    }
    
    /**
     * Convert UpdateClaimDataRequest (API v1) to ClaimDataUpdateDto (internal)
     * 
     * @since Provider Portal Security Fix (Phase 0)
     */
    public ClaimDataUpdateDto toDataUpdateDto(UpdateClaimDataRequest request) {
        return ClaimDataUpdateDto.builder()
                .doctorName(request.getDoctorName())
                .diagnosisCode(request.getDiagnosisCode())
                .diagnosisDescription(request.getDiagnosisDescription())
                .preAuthorizationId(request.getPreAuthorizationId())
                .lines(request.getLines() != null ? request.getLines().stream()
                        .map(line -> ClaimLineDto.builder()
                                .medicalServiceId(line.getMedicalServiceId())
                                .quantity(line.getQuantity())
                                .serviceCategoryId(line.getServiceCategoryId())
                                .serviceCategoryName(line.getServiceCategoryName())
                                .unitPrice(line.getGrossAmount())
                                .refusedAmount(line.getRefusedAmount())
                                .rejected(line.getRejected())
                                .rejectionReason(line.getRejectionReason())
                                .build())
                        .collect(Collectors.toList()) : null)
                .build();
    }
    
    /**
     * Convert ReviewClaimRequest (API v1) to ClaimReviewDto (internal)
     * 
     * @since Provider Portal Security Fix (Phase 0)
     */
    public ClaimReviewDto toReviewDto(ReviewClaimRequest request) {
        return ClaimReviewDto.builder()
                .status(request.getStatus())
                .reviewerComment(request.getReviewerComment())
                .approvedAmount(request.getApprovedAmount())
                .build();
    }
    
    /**
     * Convert ApproveClaimRequest (API v1) to ClaimApproveDto (internal)
     * 
     * ⚠️ CRITICAL: This conversion MUST NOT include approvedAmount.
     * The backend service layer calculates the approved amount.
     */
    public ClaimApproveDto toApproveDto(ApproveClaimRequest request) {
        return ClaimApproveDto.builder()
                .notes(request.getNotes())
                .useSystemCalculation(request.getUseSystemCalculation())
                // ✅ approvedAmount is NOT set - backend calculates it
                .build();
    }
    
    /**
     * Convert RejectClaimRequest (API v1) to ClaimRejectDto (internal)
     */
    public ClaimRejectDto toRejectDto(RejectClaimRequest request) {
        return ClaimRejectDto.builder()
                .rejectionReason(request.getRejectionReason())
                .rejectionCode(request.getRejectionCode())
                .build();
    }
    
    /**
     * Convert ReturnForInfoClaimRequest (API v1) to ClaimReturnForInfoDto (internal)
     */
    public ClaimReturnForInfoDto toReturnForInfoDto(ReturnForInfoClaimRequest request) {
        return ClaimReturnForInfoDto.builder()
                .reason(request.getReason())
                .requiredDocuments(request.getRequiredDocuments())
                .build();
    }
    
    /**
     * Convert SettleClaimRequest (API v1) to ClaimSettleDto (internal)
     * 
     * @deprecated Settlement endpoint is deprecated
     */
    @Deprecated
    public ClaimSettleDto toSettleDto(SettleClaimRequest request) {
        return ClaimSettleDto.builder()
                .paymentReference(request.getPaymentReference())
                .notes(request.getNotes())
                // ✅ settlementAmount is NOT set - backend validates against approved amount
                .build();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL DTOs → RESPONSE CONTRACTS (Outbound)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Convert ClaimViewDto (internal) to ClaimResponse (API v1)
     */
    public ClaimResponse toResponse(ClaimViewDto dto) {
        return ClaimResponse.builder()
                // Identification
                .id(dto.getId())
                .claimNumber(dto.getClaimNumber())
                
                // Related entities
                .memberId(dto.getMemberId())
                .memberFullName(dto.getMemberFullName())
                .memberName(dto.getMemberName())
                .memberNationalNumber(dto.getMemberNationalNumber())
                .employerId(dto.getEmployerId())
                .employerName(dto.getEmployerName())
                .employerCode(dto.getEmployerCode())
                .insuranceCompanyName(dto.getInsuranceCompanyName())
                .insuranceCompanyCode(dto.getInsuranceCompanyCode())
                .benefitPackageId(dto.getBenefitPackageId())
                .benefitPackageName(dto.getBenefitPackageName())
                .benefitPackageCode(dto.getBenefitPackageCode())
                .preAuthorizationId(dto.getPreApprovalId())
                .preAuthorizationStatus(dto.getPreApprovalStatus())
                
                // Visit information
                .visitId(dto.getVisitId())
                .visitType(dto.getVisitType())
                .serviceDate(dto.getServiceDate())
                
                // Provider information
                .providerId(dto.getProviderId())
                .providerName(dto.getProviderName())
                .doctorName(dto.getDoctorName())
                
                // Diagnosis
                .diagnosisCode(dto.getDiagnosisCode())
                .diagnosisDescription(dto.getDiagnosisDescription())
                
                // Financial snapshot (READ-ONLY)
                .requestedAmount(dto.getRequestedAmount())
                .totalAmount(dto.getTotalAmount())
                .approvedAmount(dto.getApprovedAmount())
                .differenceAmount(dto.getDifferenceAmount())
                .patientCoPay(dto.getPatientCoPay())
                .netProviderAmount(dto.getNetProviderAmount())
                .coPayPercent(dto.getCoPayPercent())
                .deductibleApplied(dto.getDeductibleApplied())
                
                // Settlement information (READ-ONLY)
                .paymentReference(dto.getPaymentReference())
                .settledAt(dto.getSettledAt())
                .settlementNotes(dto.getSettlementNotes())
                .settlementBatchId(dto.getSettlementBatchId())
                .settlementBatchNumber(dto.getSettlementBatchNumber())
                
                // SLA tracking (READ-ONLY)
                .expectedCompletionDate(dto.getExpectedCompletionDate())
                .actualCompletionDate(dto.getActualCompletionDate())
                .withinSla(dto.getWithinSla())
                .businessDaysTaken(dto.getBusinessDaysTaken())
                .slaDaysConfigured(dto.getSlaDaysConfigured())
                .slaStatus(dto.getSlaStatus())
                
                // Status and workflow (READ-ONLY)
                .status(dto.getStatus())
                .statusLabel(dto.getStatusLabel())
                .reviewerComment(dto.getReviewerComment())
                .reviewedAt(dto.getReviewedAt())
                .allowedNextStatuses(dto.getAllowedNextStatuses())
                .canEdit(dto.getCanEdit())
                
                // Line items and attachments
                .serviceCount(dto.getServiceCount())
                .attachmentsCount(dto.getAttachmentsCount())
                .lines(dto.getLines() != null ? dto.getLines().stream()
                        .map(this::toClaimLineResponse)
                        .collect(Collectors.toList()) : null)
                .attachments(dto.getAttachments() != null ? dto.getAttachments().stream()
                        .map(this::toClaimAttachmentResponse)
                        .collect(Collectors.toList()) : null)
                
                // Audit trail (READ-ONLY)
                .active(dto.getActive())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .createdBy(dto.getCreatedBy())
                .updatedBy(dto.getUpdatedBy())
                
                // Backlog Support
                .claimSource(dto.getClaimSource())
                .claimSourceLabel(dto.getClaimSourceLabel())
                .isBacklog(dto.getIsBacklog())
                .legacyReferenceNumber(dto.getLegacyReferenceNumber())
                .enteredAt(dto.getEnteredAt())
                .enteredBy(dto.getEnteredBy())
                
                .build();
    }
    
    /**
     * Convert ClaimLineDto to ClaimLineResponse
     */
    private ClaimResponse.ClaimLineResponse toClaimLineResponse(ClaimLineDto dto) {
        return ClaimResponse.ClaimLineResponse.builder()
                .id(dto.getId())
                .medicalServiceId(dto.getMedicalServiceId())
                .medicalServiceName(dto.getServiceName()) // Maps to serviceName
                .medicalServiceCode(dto.getServiceCode()) // Maps to serviceCode
                                .serviceCategoryId(dto.getServiceCategoryId())
                                .serviceCategoryName(dto.getServiceCategoryName())
                .quantity(dto.getQuantity())
                .unitPrice(dto.getUnitPrice())
                .totalPrice(dto.getTotalPrice())
                .refusedAmount(dto.getRefusedAmount())
                .approvedPrice(null) // Not in ClaimLineDto - would need to be added
                .notes(null) // Not in ClaimLineDto
                .active(true) // Not in ClaimLineDto - default to true
                .build();
    }
    
    /**
     * Convert ClaimAttachmentDto to ClaimAttachmentResponse
     */
    private ClaimResponse.ClaimAttachmentResponse toClaimAttachmentResponse(ClaimAttachmentDto dto) {
        return ClaimResponse.ClaimAttachmentResponse.builder()
                .id(dto.getId())
                .fileName(dto.getFileName())
                .fileType(dto.getFileType())
                .fileSize(null) // Not in ClaimAttachmentDto
                .fileUrl(dto.getFileUrl())
                .description(dto.getAttachmentType()) // Use attachmentType as description
                .isRequired(false) // Not in ClaimAttachmentDto - default to false
                .uploadedAt(dto.getCreatedAt()) // Use createdAt as uploadedAt
                .uploadedBy(null) // Not in ClaimAttachmentDto
                .build();
    }
    
    /**
     * Convert paginated ClaimViewDto to ClaimListResponse (API v1)
     */
    public ClaimListResponse toListResponse(Page<ClaimViewDto> page) {
        return ClaimListResponse.builder()
                .items(page.getContent().stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList()))
                .total(page.getTotalElements())
                .page(page.getNumber() + 1) // Convert 0-indexed to 1-indexed
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}

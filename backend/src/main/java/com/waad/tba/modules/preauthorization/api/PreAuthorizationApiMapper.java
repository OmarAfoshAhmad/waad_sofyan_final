package com.waad.tba.modules.preauthorization.api;

import com.waad.tba.modules.preauthorization.api.request.*;
import com.waad.tba.modules.preauthorization.api.response.PreAuthorizationListResponse;
import com.waad.tba.modules.preauthorization.api.response.PreAuthorizationResponse;
import com.waad.tba.modules.preauthorization.dto.*;
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
public class PreAuthorizationApiMapper {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // REQUEST CONTRACTS → INTERNAL DTOs (Inbound)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Convert CreatePreAuthorizationRequest (API v1) to PreAuthorizationCreateDto (internal)
     */
    public PreAuthorizationCreateDto toCreateDto(CreatePreAuthorizationRequest request) {
        return PreAuthorizationCreateDto.builder()
                .visitId(request.getVisitId())
                .medicalServiceId(request.getMedicalServiceId())
                .memberId(request.getMemberId())
                .providerId(request.getProviderId())
                .diagnosisCode(request.getDiagnosisCode())
                .diagnosisDescription(request.getDiagnosisDescription())
                .requestDate(request.getRequestDate())
                .currency(request.getCurrency())
                .priority(request.getPriority())
                .notes(request.getNotes())
                .serviceCategoryId(request.getServiceCategoryId())
                .serviceCategoryName(request.getServiceCategoryName())
                .emailRequestId(request.getEmailRequestId())
                .build();
    }
    
    /**
     * Convert UpdatePreAuthorizationRequest (API v1) to PreAuthorizationUpdateDto (internal)
     */
    public PreAuthorizationUpdateDto toUpdateDto(UpdatePreAuthorizationRequest request) {
        return PreAuthorizationUpdateDto.builder()
                .priority(request.getPriority())
                .diagnosisCode(request.getDiagnosisCode())
                .diagnosisDescription(request.getDiagnosisDescription())
                .notes(request.getNotes())
                .expiryDays(request.getExpiryDays())
                .build();
    }
    
    /**
     * Convert ApprovePreAuthorizationRequest (API v1) to PreAuthorizationApproveDto (internal)
     * 
     * ⚠️ CRITICAL: This conversion MUST NOT include approvedAmount or copayPercentage.
     * The backend service layer calculates these values.
     */
    public PreAuthorizationApproveDto toApproveDto(ApprovePreAuthorizationRequest request) {
        return PreAuthorizationApproveDto.builder()
                .approvalNotes(request.getApprovalNotes())
                // ✅ approvedAmount is NOT set - backend calculates it
                // ✅ copayPercentage is NOT set - backend calculates it
                .build();
    }
    
    /**
     * Convert RejectPreAuthorizationRequest (API v1) to PreAuthorizationRejectDto (internal)
     */
    public PreAuthorizationRejectDto toRejectDto(RejectPreAuthorizationRequest request) {
        return PreAuthorizationRejectDto.builder()
                .rejectionReason(request.getRejectionReason())
                .build();
    }
    
    /**
     * Convert UpdatePreAuthDataRequest (API v1) to PreAuthDataUpdateDto (internal)
     * 
     * @since Provider Portal Security Fix (Phase 3)
     */
    public PreAuthDataUpdateDto toDataUpdateDto(UpdatePreAuthDataRequest request) {
        return PreAuthDataUpdateDto.builder()
                .expectedServiceDate(request.getExpectedServiceDate())
                .clinicalJustification(request.getClinicalJustification())
                .notes(request.getNotes())
                .priority(request.getPriority())
                .build();
    }
    
    /**
     * Convert ReviewPreAuthRequest (API v1) to PreAuthReviewDto (internal)
     * 
     * @since Provider Portal Security Fix (Phase 3)
     */
    public PreAuthReviewDto toReviewDto(ReviewPreAuthRequest request) {
        return PreAuthReviewDto.builder()
                .status(request.getStatus())
                .reviewerComment(request.getReviewerComment())
                .approvedAmount(request.getApprovedAmount())
                .copayPercentage(request.getCopayPercentage())
                .build();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL DTOs → RESPONSE CONTRACTS (Outbound)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Convert PreAuthorizationResponseDto (internal) to PreAuthorizationResponse (API v1)
     */
    public PreAuthorizationResponse toResponse(PreAuthorizationResponseDto dto) {
        return PreAuthorizationResponse.builder()
                // Identification
                .id(dto.getId())
                .referenceNumber(dto.getReferenceNumber())
                
                // Related entities
                .visitId(dto.getVisitId())
                .visitDate(dto.getVisitDate())
                .visitType(dto.getVisitType())
                .memberId(dto.getMemberId())
                .memberName(dto.getMemberName())
                .memberCardNumber(dto.getMemberCardNumber())
                .memberNationalNumber(dto.getMemberNationalNumber())
                .employerId(dto.getEmployerId())
                .employerName(dto.getEmployerName())
                .employerCode(dto.getEmployerCode())
                .providerId(dto.getProviderId())
                .providerName(dto.getProviderName())
                .providerLicense(dto.getProviderLicense())
                .medicalServiceId(dto.getMedicalServiceId())
                .serviceCode(dto.getServiceCode())
                .serviceName(dto.getServiceName())
                .serviceCategoryId(dto.getServiceCategoryId())
                .serviceCategoryName(dto.getServiceCategoryName())
                .requiresPA(dto.getRequiresPA())
                .diagnosisCode(dto.getDiagnosisCode())
                .diagnosisDescription(dto.getDiagnosisDescription())
                
                // Dates (READ-ONLY)
                .requestDate(dto.getRequestDate())
                .expiryDate(dto.getExpiryDate())
                .daysUntilExpiry(dto.getDaysUntilExpiry())
                
                // Decision data (READ-ONLY)
                .contractPrice(dto.getContractPrice())
                .approvedAmount(dto.getApprovedAmount())
                .copayAmount(dto.getCopayAmount())
                .copayPercentage(dto.getCopayPercentage())
                .insuranceCoveredAmount(dto.getInsuranceCoveredAmount())
                .currency(dto.getCurrency())
                
                // Status and workflow (READ-ONLY)
                .status(dto.getStatus())
                .priority(dto.getPriority())
                
                // Additional information (READ-ONLY)
                .notes(dto.getNotes())
                .rejectionReason(dto.getRejectionReason())
                
                // Business flags (READ-ONLY)
                .hasContract(dto.getHasContract())
                .isValid(dto.getIsValid())
                .isExpired(dto.getIsExpired())
                .canBeApproved(dto.getCanBeApproved())
                .canBeRejected(dto.getCanBeRejected())
                .canBeCancelled(dto.getCanBeCancelled())
                
                // Audit trail (READ-ONLY)
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .createdBy(dto.getCreatedBy())
                .updatedBy(dto.getUpdatedBy())
                .approvedAt(dto.getApprovedAt())
                .approvedBy(dto.getApprovedBy())
                .active(dto.getActive())
                
                .build();
    }
    
    /**
     * Convert paginated PreAuthorizationResponseDto to PreAuthorizationListResponse (API v1)
     */
    public PreAuthorizationListResponse toListResponse(Page<PreAuthorizationResponseDto> page) {
        return PreAuthorizationListResponse.builder()
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

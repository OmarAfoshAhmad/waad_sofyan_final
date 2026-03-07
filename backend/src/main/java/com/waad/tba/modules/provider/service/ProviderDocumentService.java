package com.waad.tba.modules.provider.service;

import com.waad.tba.modules.claim.entity.ClaimAttachment;
import com.waad.tba.modules.claim.repository.ClaimAttachmentRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationAttachment;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationAttachmentRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.dto.ProviderDocumentDto;
import com.waad.tba.modules.visit.entity.VisitAttachment;
import com.waad.tba.modules.visit.repository.VisitAttachmentRepository;
import com.waad.tba.modules.visit.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Provider Documents Service
 * 
 * Aggregates documents from Visits, PreAuthorizations, and Claims
 * into a unified view for the Provider Portal.
 * 
 * SECURITY: Provider can only see documents associated with their providerId
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderDocumentService {

    private final VisitRepository visitRepository;
    private final VisitAttachmentRepository visitAttachmentRepository;
    private final PreAuthorizationRepository preAuthorizationRepository;
    private final PreAuthorizationAttachmentRepository preAuthAttachmentRepository;
    private final ClaimRepository claimRepository;
    private final ClaimAttachmentRepository claimAttachmentRepository;
    private final EntityManager entityManager;

    /**
     * Get all documents for a provider (unified view)
     * 
     * @param providerId Provider ID (from JWT)
     * @param referenceType Optional filter: VISIT | PRE_AUTH | CLAIM
     * @param status Optional filter: REQUIRED | UPLOADED | APPROVED | REJECTED
     * @param fromDate Optional filter: documents from this date
     * @param toDate Optional filter: documents until this date
     * @param pageable Pagination info
     * @return Page of ProviderDocumentDto
     */
    @Transactional(readOnly = true)
    public Page<ProviderDocumentDto> getProviderDocuments(
            Long providerId,
            String referenceType,
            String status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        
        log.info("📄 Fetching documents for provider {} | filter: type={}, status={}, from={}, to={}",
                providerId, referenceType, status, fromDate, toDate);
        
        List<ProviderDocumentDto> allDocuments = new ArrayList<>();
        
        // Fetch based on filter or all
        if (referenceType == null || referenceType.equalsIgnoreCase(ProviderDocumentDto.REF_TYPE_VISIT)) {
            allDocuments.addAll(fetchVisitDocuments(providerId, status, fromDate, toDate));
        }
        
        if (referenceType == null || referenceType.equalsIgnoreCase(ProviderDocumentDto.REF_TYPE_PRE_AUTH)) {
            allDocuments.addAll(fetchPreAuthDocuments(providerId, status, fromDate, toDate));
        }
        
        if (referenceType == null || referenceType.equalsIgnoreCase(ProviderDocumentDto.REF_TYPE_CLAIM)) {
            allDocuments.addAll(fetchClaimDocuments(providerId, status, fromDate, toDate));
        }
        
        // Sort by uploadedAt descending (null values at the end for REQUIRED docs)
        allDocuments.sort(Comparator.comparing(
            ProviderDocumentDto::getUploadedAt,
            Comparator.nullsLast(Comparator.reverseOrder())
        ));
        
        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allDocuments.size());
        
        List<ProviderDocumentDto> pageContent = start < allDocuments.size() 
            ? allDocuments.subList(start, end) 
            : List.of();
        
        log.info("📄 Returning {} documents (page {}/{})", 
                pageContent.size(), pageable.getPageNumber(), 
                (int) Math.ceil((double) allDocuments.size() / pageable.getPageSize()));
        
        return new PageImpl<>(pageContent, pageable, allDocuments.size());
    }

    // ==================== VISIT DOCUMENTS ====================
    
    private List<ProviderDocumentDto> fetchVisitDocuments(
            Long providerId, String statusFilter, LocalDate fromDate, LocalDate toDate) {
        
        List<ProviderDocumentDto> documents = new ArrayList<>();
        
        // Get all visits for this provider
        var visits = visitRepository.findByProviderIdAndActiveTrue(providerId);
        
        for (var visit : visits) {
            // Date filter
            if (fromDate != null && visit.getVisitDate().isBefore(fromDate)) continue;
            if (toDate != null && visit.getVisitDate().isAfter(toDate)) continue;
            
            // Get attachments for this visit
            var attachments = visitAttachmentRepository.findByVisitId(visit.getId());
            
            for (var attachment : attachments) {
                String status = ProviderDocumentDto.STATUS_UPLOADED; // Default for existing attachments
                
                if (statusFilter != null && !statusFilter.equalsIgnoreCase(status)) continue;
                
                documents.add(mapVisitAttachment(attachment, visit));
            }
        }
        
        return documents;
    }
    
    private ProviderDocumentDto mapVisitAttachment(VisitAttachment attachment, com.waad.tba.modules.visit.entity.Visit visit) {
        String docType = attachment.getAttachmentType() != null 
            ? attachment.getAttachmentType().name() 
            : "OTHER";
        
        return ProviderDocumentDto.builder()
                .id(attachment.getId())
                .referenceType(ProviderDocumentDto.REF_TYPE_VISIT)
                .referenceId(visit.getId())
                .referenceNumber("V-" + visit.getId())
                .documentType(docType)
                .documentTypeLabel(ProviderDocumentDto.getDocumentTypeLabelArabic(docType))
                .status(ProviderDocumentDto.STATUS_UPLOADED)
                .statusLabel(ProviderDocumentDto.getStatusLabelArabic(ProviderDocumentDto.STATUS_UPLOADED))
                .fileName(attachment.getOriginalFileName())
                .fileSize(attachment.getFileSize())
                .fileType(attachment.getFileType())
                .uploadedAt(attachment.getCreatedAt())
                .uploadedBy(attachment.getUploadedBy())
                .memberName(visit.getMember() != null ? visit.getMember().getFullName() : null)
                .serviceName(visit.getMedicalServiceName())
                .downloadUrl("/api/v1/visits/" + visit.getId() + "/attachments/" + attachment.getId())
                .build();
    }

    // ==================== PRE-AUTHORIZATION DOCUMENTS ====================
    
    private List<ProviderDocumentDto> fetchPreAuthDocuments(
            Long providerId, String statusFilter, LocalDate fromDate, LocalDate toDate) {
        
        List<ProviderDocumentDto> documents = new ArrayList<>();
        
        // Get all pre-authorizations for this provider
        var preAuths = preAuthorizationRepository.findByProviderIdAndActiveTrue(providerId);
        
        for (var preAuth : preAuths) {
            // Date filter
            if (fromDate != null && preAuth.getRequestDate() != null && preAuth.getRequestDate().isBefore(fromDate)) continue;
            if (toDate != null && preAuth.getRequestDate() != null && preAuth.getRequestDate().isAfter(toDate)) continue;
            
            // Get attachments for this pre-auth
            var attachments = preAuthAttachmentRepository.findByPreAuthorizationId(preAuth.getId());
            
            for (var attachment : attachments) {
                String status = ProviderDocumentDto.STATUS_UPLOADED;
                
                if (statusFilter != null && !statusFilter.equalsIgnoreCase(status)) continue;
                
                documents.add(mapPreAuthAttachment(attachment, preAuth));
            }
        }
        
        return documents;
    }
    
    private ProviderDocumentDto mapPreAuthAttachment(
            PreAuthorizationAttachment attachment, 
            com.waad.tba.modules.preauthorization.entity.PreAuthorization preAuth) {
        
        String docType = attachment.getAttachmentType() != null 
            ? attachment.getAttachmentType() 
            : "OTHER";
        
        return ProviderDocumentDto.builder()
                .id(attachment.getId())
                .referenceType(ProviderDocumentDto.REF_TYPE_PRE_AUTH)
                .referenceId(preAuth.getId())
                .referenceNumber(preAuth.getReferenceNumber() != null 
                    ? preAuth.getReferenceNumber() 
                    : "PA-" + preAuth.getId())
                .documentType(docType)
                .documentTypeLabel(ProviderDocumentDto.getDocumentTypeLabelArabic(docType))
                .status(ProviderDocumentDto.STATUS_UPLOADED)
                .statusLabel(ProviderDocumentDto.getStatusLabelArabic(ProviderDocumentDto.STATUS_UPLOADED))
                .fileName(attachment.getOriginalFileName())
                .fileSize(attachment.getFileSize())
                .fileType(attachment.getFileType())
                .uploadedAt(attachment.getCreatedAt())
                .uploadedBy(attachment.getCreatedBy())
                .memberName(null) // PreAuthorization doesn't have Member entity - only memberId
                .serviceName(preAuth.getServiceName())
                .downloadUrl("/api/v1/pre-authorizations/" + preAuth.getId() + "/attachments/" + attachment.getId())
                .build();
    }

    // ==================== CLAIM DOCUMENTS ====================
    
    private List<ProviderDocumentDto> fetchClaimDocuments(
            Long providerId, String statusFilter, LocalDate fromDate, LocalDate toDate) {
        
        List<ProviderDocumentDto> documents = new ArrayList<>();

        // Include legacy claims where provider_id may be null but visit.provider_id is set
        var claims = entityManager.createQuery(
            "SELECT c FROM Claim c WHERE c.active = true AND (c.providerId = :providerId OR c.visit.providerId = :providerId)",
            com.waad.tba.modules.claim.entity.Claim.class)
            .setParameter("providerId", providerId)
            .getResultList();
        
        for (var claim : claims) {
            // Date filter (use service date)
            LocalDate claimDate = claim.getServiceDate();
            if (fromDate != null && claimDate != null && claimDate.isBefore(fromDate)) continue;
            if (toDate != null && claimDate != null && claimDate.isAfter(toDate)) continue;
            
            // Get attachments for this claim
            var attachments = claimAttachmentRepository.findByClaimId(claim.getId());
            
            for (var attachment : attachments) {
                String status = ProviderDocumentDto.STATUS_UPLOADED;
                
                if (statusFilter != null && !statusFilter.equalsIgnoreCase(status)) continue;
                
                documents.add(mapClaimAttachment(attachment, claim));
            }
        }
        
        return documents;
    }
    
    private ProviderDocumentDto mapClaimAttachment(
            ClaimAttachment attachment, 
            com.waad.tba.modules.claim.entity.Claim claim) {
        
        String docType = attachment.getAttachmentType() != null 
            ? attachment.getAttachmentType().name() 
            : "OTHER";
        
        return ProviderDocumentDto.builder()
                .id(attachment.getId())
                .referenceType(ProviderDocumentDto.REF_TYPE_CLAIM)
                .referenceId(claim.getId())
                .referenceNumber("CL-" + claim.getId())
                .documentType(docType)
                .documentTypeLabel(ProviderDocumentDto.getDocumentTypeLabelArabic(docType))
                .status(ProviderDocumentDto.STATUS_UPLOADED)
                .statusLabel(ProviderDocumentDto.getStatusLabelArabic(ProviderDocumentDto.STATUS_UPLOADED))
                .fileName(attachment.getOriginalFileName())
                .fileSize(attachment.getFileSize())
                .fileType(attachment.getFileType())
                .uploadedAt(attachment.getCreatedAt())
                .uploadedBy(attachment.getUploadedBy())
                .memberName(claim.getMember() != null ? claim.getMember().getFullName() : null)
                .downloadUrl("/api/v1/claims/" + claim.getId() + "/attachments/" + attachment.getId())
                .build();
    }

    // ==================== STATISTICS ====================
    
    /**
     * Get document statistics for a provider
     */
    @Transactional(readOnly = true)
    public ProviderDocumentStats getProviderDocumentStats(Long providerId) {
        long visitDocs = 0;
        long preAuthDocs = 0;
        long claimDocs = 0;
        
        // Count visit attachments
        var visits = visitRepository.findByProviderIdAndActiveTrue(providerId);
        for (var visit : visits) {
            visitDocs += visitAttachmentRepository.countByVisitId(visit.getId());
        }
        
        // Count pre-auth attachments
        var preAuths = preAuthorizationRepository.findByProviderIdAndActiveTrue(providerId);
        for (var preAuth : preAuths) {
            preAuthDocs += preAuthAttachmentRepository.countByPreAuthorizationId(preAuth.getId());
        }
        
        // Count claim attachments (with fallback via visit.providerId)
        var claims = entityManager.createQuery(
                "SELECT c FROM Claim c WHERE c.active = true AND (c.providerId = :providerId OR c.visit.providerId = :providerId)",
                com.waad.tba.modules.claim.entity.Claim.class)
            .setParameter("providerId", providerId)
            .getResultList();
        for (var claim : claims) {
            claimDocs += claimAttachmentRepository.countByClaimId(claim.getId());
        }
        
        return ProviderDocumentStats.builder()
                .totalDocuments(visitDocs + preAuthDocs + claimDocs)
                .visitDocuments(visitDocs)
                .preAuthDocuments(preAuthDocs)
                .claimDocuments(claimDocs)
                .build();
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProviderDocumentStats {
        private long totalDocuments;
        private long visitDocuments;
        private long preAuthDocuments;
        private long claimDocuments;
    }
}

package com.waad.tba.modules.provider.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.provider.dto.ProviderClaimReportDto;
import com.waad.tba.modules.provider.dto.ProviderPreAuthReportDto;
import com.waad.tba.modules.provider.dto.ProviderVisitReportDto;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider Reports Service
 * 
 * Generates provider-specific reports with proper scoping and filtering.
 * All reports are strictly limited to the provider's own data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("deprecation")
public class ProviderReportsService {
    
    private final EntityManager entityManager;
    
    /**
     * Get claims report for provider
     */
    public Page<ProviderClaimReportDto> getClaimsReport(
            Long providerId, 
            LocalDate fromDate, 
            LocalDate toDate,
            ClaimStatus status,
            String memberBarcode,
            Pageable pageable) {
        
        log.debug("Generating claims report: provider={}, fromDate={}, toDate={}", 
                providerId, fromDate, toDate);
        
        StringBuilder jpql = new StringBuilder(
            "SELECT c FROM Claim c WHERE (c.providerId = :providerId OR c.visit.providerId = :providerId)");
        
        if (fromDate != null) {
            jpql.append(" AND c.createdAt >= :fromDate");
        }
        if (toDate != null) {
            jpql.append(" AND c.createdAt <= :toDate");
        }
        if (status != null) {
            jpql.append(" AND c.status = :status");
        }
        if (memberBarcode != null && !memberBarcode.isEmpty()) {
            jpql.append(" AND c.member.barcode = :memberBarcode");
        }
        
        jpql.append(" ORDER BY c.createdAt DESC");
        
        TypedQuery<Claim> query = entityManager.createQuery(jpql.toString(), Claim.class);
        query.setParameter("providerId", providerId);
        
        if (fromDate != null) {
            query.setParameter("fromDate", fromDate.atStartOfDay());
        }
        if (toDate != null) {
            query.setParameter("toDate", toDate.atTime(23, 59, 59));
        }
        if (status != null) {
            query.setParameter("status", status);
        }
        if (memberBarcode != null && !memberBarcode.isEmpty()) {
            query.setParameter("memberBarcode", memberBarcode);
        }
        
        // Get total count
        String countJpql = jpql.toString().replace("SELECT c FROM", "SELECT COUNT(c) FROM");
        countJpql = countJpql.substring(0, countJpql.indexOf("ORDER BY"));
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
        countQuery.setParameter("providerId", providerId);
        if (fromDate != null) countQuery.setParameter("fromDate", fromDate.atStartOfDay());
        if (toDate != null) countQuery.setParameter("toDate", toDate.atTime(23, 59, 59));
        if (status != null) countQuery.setParameter("status", status);
        if (memberBarcode != null && !memberBarcode.isEmpty()) countQuery.setParameter("memberBarcode", memberBarcode);
        
        Long total = countQuery.getSingleResult();
        
        // Get paginated results
        List<Claim> claims = query
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        
        // Map to DTOs
        List<ProviderClaimReportDto> dtos = claims.stream()
                .map(this::mapClaimToReportDto)
                .toList();
        
        return new PageImpl<>(dtos, pageable, total);
    }
    
    /**
     * Get pre-auth report for provider
     */
    public Page<ProviderPreAuthReportDto> getPreAuthReport(
            Long providerId,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            String memberBarcode,
            Pageable pageable) {
        
        log.debug("Generating pre-auth report: provider={}", providerId);

        PreAuthorization.PreAuthStatus preAuthStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                preAuthStatus = PreAuthorization.PreAuthStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.warn("Ignoring invalid pre-auth status filter '{}' for provider {}", status, providerId);
            }
        }

        StringBuilder jpql = new StringBuilder(
            "SELECT p FROM PreAuthorization p WHERE p.providerId = :providerId AND p.active = true");

        if (fromDate != null) {
            jpql.append(" AND p.requestDate >= :fromDate");
        }
        if (toDate != null) {
            jpql.append(" AND p.requestDate <= :toDate");
        }
        if (preAuthStatus != null) {
            jpql.append(" AND p.status = :status");
        }
        if (memberBarcode != null && !memberBarcode.isEmpty()) {
            jpql.append(" AND p.visit.member.barcode = :memberBarcode");
        }

        jpql.append(" ORDER BY p.requestDate DESC, p.id DESC");

        TypedQuery<PreAuthorization> query = entityManager.createQuery(jpql.toString(), PreAuthorization.class);
        query.setParameter("providerId", providerId);
        if (fromDate != null) {
            query.setParameter("fromDate", fromDate);
        }
        if (toDate != null) {
            query.setParameter("toDate", toDate);
        }
        if (preAuthStatus != null) {
            query.setParameter("status", preAuthStatus);
        }
        if (memberBarcode != null && !memberBarcode.isEmpty()) {
            query.setParameter("memberBarcode", memberBarcode);
        }

        String countJpql = jpql.toString().replace("SELECT p FROM", "SELECT COUNT(p) FROM");
        countJpql = countJpql.substring(0, countJpql.indexOf("ORDER BY"));
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
        countQuery.setParameter("providerId", providerId);
        if (fromDate != null) countQuery.setParameter("fromDate", fromDate);
        if (toDate != null) countQuery.setParameter("toDate", toDate);
        if (preAuthStatus != null) countQuery.setParameter("status", preAuthStatus);
        if (memberBarcode != null && !memberBarcode.isEmpty()) countQuery.setParameter("memberBarcode", memberBarcode);

        Long total = countQuery.getSingleResult();

        List<PreAuthorization> preAuths = query
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        List<ProviderPreAuthReportDto> dtos = preAuths.stream()
                .map(this::mapPreAuthToReportDto)
                .toList();

        return new PageImpl<>(dtos, pageable, total);
    }
    
    /**
     * Get visits report for provider
     */
    public Page<ProviderVisitReportDto> getVisitsReport(
            Long providerId,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            String memberBarcode,
            Pageable pageable) {
        
        log.debug("Generating visits report: provider={}", providerId);

        VisitStatus visitStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                visitStatus = VisitStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.warn("Ignoring invalid visit status filter '{}' for provider {}", status, providerId);
            }
        }

        StringBuilder jpql = new StringBuilder(
            "SELECT v FROM Visit v WHERE v.providerId = :providerId AND v.active = true");

        if (fromDate != null) {
            jpql.append(" AND v.visitDate >= :fromDate");
        }
        if (toDate != null) {
            jpql.append(" AND v.visitDate <= :toDate");
        }
        if (visitStatus != null) {
            jpql.append(" AND v.status = :status");
        }
        if (memberBarcode != null && !memberBarcode.isEmpty()) {
            jpql.append(" AND v.member.barcode = :memberBarcode");
        }

        jpql.append(" ORDER BY v.visitDate DESC, v.id DESC");

        TypedQuery<Visit> query = entityManager.createQuery(jpql.toString(), Visit.class);
        query.setParameter("providerId", providerId);
        if (fromDate != null) {
            query.setParameter("fromDate", fromDate);
        }
        if (toDate != null) {
            query.setParameter("toDate", toDate);
        }
        if (visitStatus != null) {
            query.setParameter("status", visitStatus);
        }
        if (memberBarcode != null && !memberBarcode.isEmpty()) {
            query.setParameter("memberBarcode", memberBarcode);
        }

        String countJpql = jpql.toString().replace("SELECT v FROM", "SELECT COUNT(v) FROM");
        countJpql = countJpql.substring(0, countJpql.indexOf("ORDER BY"));
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
        countQuery.setParameter("providerId", providerId);
        if (fromDate != null) countQuery.setParameter("fromDate", fromDate);
        if (toDate != null) countQuery.setParameter("toDate", toDate);
        if (visitStatus != null) countQuery.setParameter("status", visitStatus);
        if (memberBarcode != null && !memberBarcode.isEmpty()) countQuery.setParameter("memberBarcode", memberBarcode);

        Long total = countQuery.getSingleResult();

        List<Visit> visits = query
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        List<ProviderVisitReportDto> dtos = visits.stream()
                .map(this::mapVisitToReportDto)
                .toList();

        return new PageImpl<>(dtos, pageable, total);
    }

    private ProviderVisitReportDto mapVisitToReportDto(Visit visit) {
        int claimCount = visit.getClaims() != null ? visit.getClaims().size() : 0;

        return ProviderVisitReportDto.builder()
                .visitId(visit.getId())
                .visitNumber(String.valueOf(visit.getId()))
                .visitDate(visit.getVisitDate())
                .memberName(visit.getMember() != null ? visit.getMember().getFullName() : null)
                .memberBarcode(visit.getMember() != null ? visit.getMember().getBarcode() : null)
                .civilId(visit.getMember() != null ? 
                    (visit.getMember().getNationalNumber() != null ? visit.getMember().getNationalNumber() : visit.getMember().getCivilId()) : null)
                .employerName(visit.getEmployer() != null ? visit.getEmployer().getName() : null)
                .visitType(visit.getVisitType() != null ? visit.getVisitType().name() : null)
                .visitTypeLabel(visit.getVisitType() != null ? visit.getVisitType().getArabicLabel() : null)
                .chiefComplaint(visit.getDiagnosis())
                .diagnosis(visit.getDiagnosis())
                .claimCount(claimCount)
                .preAuthCount(0)
                .totalAmount(visit.getTotalAmount() != null ? visit.getTotalAmount() : BigDecimal.ZERO)
                .approvedAmount(BigDecimal.ZERO)
                .pendingAmount(BigDecimal.ZERO)
                .status(visit.getStatus() != null ? visit.getStatus().name() : null)
                .statusLabel(visit.getStatus() != null ? visit.getStatus().getLabelAr() : null)
                .createdAt(visit.getCreatedAt() != null ? visit.getCreatedAt().toLocalDate() : null)
                .closedAt(null)
                .build();
    }

        private ProviderPreAuthReportDto mapPreAuthToReportDto(PreAuthorization preAuth) {
        String status = preAuth.getStatus() != null ? preAuth.getStatus().name() : null;
        String statusLabel = preAuth.getStatus() != null ? preAuth.getStatus().getArabicLabel() : null;

        int sessionsRequested = 1;
        int sessionsApproved = (preAuth.getStatus() == PreAuthorization.PreAuthStatus.APPROVED ||
            preAuth.getStatus() == PreAuthorization.PreAuthStatus.ACKNOWLEDGED ||
            preAuth.getStatus() == PreAuthorization.PreAuthStatus.USED)
            ? 1 : 0;
        int sessionsUsed = preAuth.getStatus() == PreAuthorization.PreAuthStatus.USED ? 1 : 0;

        return ProviderPreAuthReportDto.builder()
            .preAuthId(preAuth.getId())
            .preAuthNumber(preAuth.getReferenceNumber() != null ? preAuth.getReferenceNumber() : preAuth.getPreAuthNumber())
            .requestDate(preAuth.getRequestDate())
            .validFrom(preAuth.getRequestDate())
            .validTo(preAuth.getExpiryDate())
            .memberName(preAuth.getVisit() != null && preAuth.getVisit().getMember() != null ? preAuth.getVisit().getMember().getFullName() : null)
            .memberBarcode(preAuth.getVisit() != null && preAuth.getVisit().getMember() != null ? preAuth.getVisit().getMember().getBarcode() : null)
            .civilId(preAuth.getVisit() != null && preAuth.getVisit().getMember() != null ? 
                (preAuth.getVisit().getMember().getNationalNumber() != null ? preAuth.getVisit().getMember().getNationalNumber() : preAuth.getVisit().getMember().getCivilId()) : null)
            .employerName(preAuth.getVisit() != null && preAuth.getVisit().getEmployer() != null ? preAuth.getVisit().getEmployer().getName() : null)
            .requestedAmount(preAuth.getContractPrice() != null ? preAuth.getContractPrice() : BigDecimal.ZERO)
            .approvedAmount(preAuth.getApprovedAmount() != null ? preAuth.getApprovedAmount() : BigDecimal.ZERO)
            .status(status)
            .statusLabel(statusLabel)
            .serviceName(preAuth.getServiceName())
            .sessionsRequested(sessionsRequested)
            .sessionsApproved(sessionsApproved)
            .sessionsUsed(sessionsUsed)
            .medicalJustification(preAuth.getDiagnosisDescription())
            .diagnosis(preAuth.getDiagnosisCode())
            .reviewerName(preAuth.getApprovedBy())
            .reviewerNotes(preAuth.getNotes())
            .reviewDate(preAuth.getApprovedAt() != null ? preAuth.getApprovedAt().toLocalDate() : null)
            .attachmentsCount(0)
            .build();
        }
    
    /**
     * Map Claim entity to report DTO
     */
    private ProviderClaimReportDto mapClaimToReportDto(Claim claim) {
        return ProviderClaimReportDto.builder()
                .claimId(claim.getId())
                .claimNumber(String.valueOf(claim.getId()))
                .claimDate(claim.getServiceDate())
                .submissionDate(claim.getCreatedAt() != null ? claim.getCreatedAt().toLocalDate() : null)
                .memberName(claim.getMember().getFullName())
                .memberBarcode(claim.getMember().getBarcode())
                .civilId(claim.getMember().getNationalNumber() != null ? 
                    claim.getMember().getNationalNumber() : 
                    (claim.getMember().getCivilId() != null ? claim.getMember().getCivilId() : ""))
                .employerName(claim.getMember().getEmployer() != null ? 
                        claim.getMember().getEmployer().getName() : null)
                .claimedAmount(claim.getRequestedAmount() != null ? claim.getRequestedAmount() : BigDecimal.ZERO)
                .approvedAmount(claim.getApprovedAmount() != null ? claim.getApprovedAmount() : BigDecimal.ZERO)
                .rejectedAmount(claim.getDifferenceAmount() != null ? claim.getDifferenceAmount() : BigDecimal.ZERO)
                .netAmount(claim.getNetProviderAmount() != null ? claim.getNetProviderAmount() : BigDecimal.ZERO)
                .status(claim.getStatus() != null ? claim.getStatus().name() : "UNKNOWN")
                .statusLabel(getClaimStatusLabel(claim.getStatus() != null ? claim.getStatus().name() : ""))
                .servicesCount(claim.getLines() != null ? claim.getLines().size() : 0)
                .diagnosis(claim.getDiagnosisCode())
                .reviewerNotes(claim.getReviewerComment())
                .build();
    }
    
    private String getClaimStatusLabel(String status) {
        return switch (status != null ? status : "") {
            case "DRAFT" -> "مسودة";
            case "SUBMITTED" -> "مقدمة";
            case "UNDER_REVIEW" -> "قيد المراجعة";
            case "APPROVED" -> "موافق عليها";
            case "PARTIALLY_APPROVED" -> "موافق عليها جزئياً";
            case "REJECTED" -> "مرفوضة";
            case "PAID" -> "مدفوعة";
            default -> status;
        };
    }
}

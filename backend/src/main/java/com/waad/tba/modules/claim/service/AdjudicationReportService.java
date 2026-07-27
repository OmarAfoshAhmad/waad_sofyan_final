package com.waad.tba.modules.claim.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.dto.AdjudicationReportDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adjudication Report Service.
 * 
 * Generates financial adjudication reports showing:
 * - Requested amounts from providers
 * - Patient co-pay amounts
 * - Net amounts payable to providers
 * 
 * Business Rule:
 * RequestedAmount = PatientCoPay + NetProviderAmount
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdjudicationReportService {
    
    private final ClaimRepository claimRepository;
    
    /**
     * Generate Adjudication Report.
     * 
     * @param fromDate Start date for report period
     * @param toDate End date for report period
     * @param providerName Filter by provider name (optional)
     * @param statuses Filter by claim statuses (optional, defaults to APPROVED and SETTLED)
     * @return Complete adjudication report
     */
    public AdjudicationReportDto generateReport(
            LocalDate fromDate, 
            LocalDate toDate,
            String providerName,
            List<ClaimStatus> statuses) {
        
        log.info("📊 Generating adjudication report: {} to {}", fromDate, toDate);
        
        // Default to APPROVED and SETTLED if no statuses specified
        if (statuses == null || statuses.isEmpty()) {
            statuses = List.of(ClaimStatus.APPROVED, ClaimStatus.SETTLED);
        }
        
        // Database-owned filtering: never load the complete claims history for a
        // bounded report request.
        List<Claim> claims = claimRepository.findForAdjudicationReport(
            statuses, fromDate, toDate, providerName == null ? null : providerName.trim());
        
        // Calculate totals
        BigDecimal totalRequested = BigDecimal.ZERO;
        BigDecimal totalPatientCoPay = BigDecimal.ZERO;
        BigDecimal totalNetPayable = BigDecimal.ZERO;
        
        // Group by provider
        Map<String, List<Claim>> claimsByProvider = new HashMap<>();
        
        for (Claim claim : claims) {
            String provider = claim.getProviderName() != null ? claim.getProviderName() : "غير محدد";
            claimsByProvider.computeIfAbsent(provider, k -> new ArrayList<>()).add(claim);
            
            if (claim.getRequestedAmount() != null) {
                totalRequested = totalRequested.add(claim.getRequestedAmount());
            }
            if (claim.getPatientCoPay() != null) {
                totalPatientCoPay = totalPatientCoPay.add(claim.getPatientCoPay());
            }
            if (claim.getNetProviderAmount() != null) {
                totalNetPayable = totalNetPayable.add(claim.getNetProviderAmount());
            } else if (claim.getApprovedAmount() != null) {
                // Fallback: use approved amount if net provider amount not set
                totalNetPayable = totalNetPayable.add(claim.getApprovedAmount());
            }
        }
        
        // Build provider summaries
        List<AdjudicationReportDto.ProviderSummary> providerSummaries = new ArrayList<>();
        for (Map.Entry<String, List<Claim>> entry : claimsByProvider.entrySet()) {
            BigDecimal providerRequested = BigDecimal.ZERO;
            BigDecimal providerPatientCoPay = BigDecimal.ZERO;
            BigDecimal providerNet = BigDecimal.ZERO;
            
            for (Claim c : entry.getValue()) {
                if (c.getRequestedAmount() != null) {
                    providerRequested = providerRequested.add(c.getRequestedAmount());
                }
                if (c.getPatientCoPay() != null) {
                    providerPatientCoPay = providerPatientCoPay.add(c.getPatientCoPay());
                }
                if (c.getNetProviderAmount() != null) {
                    providerNet = providerNet.add(c.getNetProviderAmount());
                } else if (c.getApprovedAmount() != null) {
                    providerNet = providerNet.add(c.getApprovedAmount());
                }
            }
            
            providerSummaries.add(AdjudicationReportDto.ProviderSummary.builder()
                .providerName(entry.getKey())
                .claimCount((long) entry.getValue().size())
                .totalRequested(providerRequested)
                .totalPatientCoPay(providerPatientCoPay)
                .netPayable(providerNet)
                .build());
        }
        
        // Build claim details - use serviceDate (canonical field)
        List<AdjudicationReportDto.ClaimSummary> claimDetails = claims.stream()
            .map(c -> AdjudicationReportDto.ClaimSummary.builder()
                .claimId(c.getId())
                .memberName(c.getMember() != null ? c.getMember().getFullName() : null)
                .memberCivilId(c.getMember() != null ? c.getMember().getNationalNumber() : null)
                .providerName(c.getProviderName())
                .visitDate(c.getServiceDate())
                .status(c.getStatus() != null ? c.getStatus().getArabicLabel() : null)
                .requestedAmount(c.getRequestedAmount())
                .patientCoPay(c.getPatientCoPay())
                .netProviderAmount(c.getNetProviderAmount() != null ? 
                    c.getNetProviderAmount() : c.getApprovedAmount())
                .reviewerComment(c.getReviewerComment())
                .build())
            .collect(Collectors.toList());
        
        // Count by status
        long approvedCount = claims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.APPROVED)
            .count();
        long settledCount = claims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.SETTLED)
            .count();
        // Must respect the same fromDate/toDate/providerName filter as
        // approvedCount/settledCount above — a global unfiltered count here
        // previously mixed system-wide rejected/pending totals into a
        // date-ranged report.
        long rejectedCount = claimRepository.findForAdjudicationReport(
            List.of(ClaimStatus.REJECTED), fromDate, toDate, providerName).size();
        long pendingCount = claimRepository.findForAdjudicationReport(
            List.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW), fromDate, toDate, providerName).size();
        
        AdjudicationReportDto report = AdjudicationReportDto.builder()
            .fromDate(fromDate)
            .toDate(toDate)
            .totalRequested(totalRequested)
            .totalPatientCoPay(totalPatientCoPay)
            .totalNetPayable(totalNetPayable)
            .totalClaims((long) claims.size())
            .approvedClaims(approvedCount)
            .settledClaims(settledCount)
            .rejectedClaims(rejectedCount)
            .pendingClaims(pendingCount)
            .providerSummaries(providerSummaries)
            .claimDetails(claimDetails)
            .build();
        
        log.info("📊 Report generated: {} claims, total requested: {}, net payable: {}", 
            claims.size(), totalRequested, totalNetPayable);
        
        return report;
    }
    
    /**
     * Generate Provider Settlement Report.
     * Returns claims ready for settlement (APPROVED status) grouped by provider.
     */
    public AdjudicationReportDto generateProviderSettlementReport(String providerName) {
        LocalDate today = LocalDate.now();
        LocalDate yearStart = today.withDayOfYear(1);
        
        return generateReport(
            yearStart, 
            today, 
            providerName, 
            List.of(ClaimStatus.APPROVED)
        );
    }
    
    /**
     * Generate Member Statement.
     * Returns all claims for a specific member.
     */
    public AdjudicationReportDto generateMemberStatement(Long memberId, LocalDate fromDate, LocalDate toDate) {
        log.info("📊 Generating member statement for member {}", memberId);
        
        List<Claim> claims = claimRepository.findByMemberId(memberId);
        
        // Filter by date - use serviceDate (canonical field)
        if (fromDate != null && toDate != null) {
            claims = claims.stream()
                .filter(c -> c.getServiceDate() != null)
                .filter(c -> !c.getServiceDate().isBefore(fromDate))
                .filter(c -> !c.getServiceDate().isAfter(toDate))
                .collect(Collectors.toList());
        }
        
        // Calculate totals
        BigDecimal totalRequested = claims.stream()
            .map(Claim::getRequestedAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalPatientCoPay = claims.stream()
            .map(Claim::getPatientCoPay)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalNetPayable = claims.stream()
            .map(c -> c.getNetProviderAmount() != null ? c.getNetProviderAmount() : c.getApprovedAmount())
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Build claim details for member statement
        List<AdjudicationReportDto.ClaimSummary> claimDetails = claims.stream()
            .map(c -> AdjudicationReportDto.ClaimSummary.builder()
                .claimId(c.getId())
                .memberName(c.getMember() != null ? c.getMember().getFullName() : null)
                .memberCivilId(c.getMember() != null ? c.getMember().getNationalNumber() : null)
                .providerName(c.getProviderName())
                .visitDate(c.getServiceDate())
                .status(c.getStatus() != null ? c.getStatus().getArabicLabel() : null)
                .requestedAmount(c.getRequestedAmount())
                .patientCoPay(c.getPatientCoPay())
                .netProviderAmount(c.getNetProviderAmount() != null ? 
                    c.getNetProviderAmount() : c.getApprovedAmount())
                .reviewerComment(c.getReviewerComment())
                .build())
            .collect(Collectors.toList());
        
        return AdjudicationReportDto.builder()
            .fromDate(fromDate)
            .toDate(toDate)
            .totalRequested(totalRequested)
            .totalPatientCoPay(totalPatientCoPay)
            .totalNetPayable(totalNetPayable)
            .totalClaims((long) claims.size())
            .claimDetails(claimDetails)
            .build();
    }
}

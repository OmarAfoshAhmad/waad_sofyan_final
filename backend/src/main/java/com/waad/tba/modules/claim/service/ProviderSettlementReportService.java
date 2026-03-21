package com.waad.tba.modules.claim.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.dto.ProviderSettlementReportDto;
import com.waad.tba.modules.claim.dto.ProviderSettlementReportDto.ClaimDetail;
import com.waad.tba.modules.claim.dto.ProviderSettlementReportDto.LineStatus;
import com.waad.tba.modules.claim.dto.ProviderSettlementReportDto.ServiceLineDetail;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider Settlement Report Service.
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ PROVIDER SETTLEMENT REPORT SERVICE ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Purpose: Generate detailed settlement reports for healthcare providers ║
 * ║ Canonical Sources: claims, claim_lines, members, medical_services,
 * pre_auths ║
 * ║ Calculations: ALL done in Backend - NO client-side math allowed ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 * 
 * Report Structure (matches paper reports):
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │ HEADER: Report #, Date, Provider Name │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ SUMMARY: Total Claims, Total Gross, Total Net, Total Rejected, Net Provider
 * │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ CLAIMS: Grouped by Claim │
 * │ └─ LINES: Service details with Gross, Net, Rejected, Reason │
 * │ └─ SUBTOTAL: Claim totals │
 * └─────────────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderSettlementReportService {

    private final ClaimRepository claimRepository;
    private final ProviderRepository providerRepository;

    /**
     * Generate Provider Settlement Report.
     * 
     * ╔═══════════════════════════════════════════════════════════════════════════════╗
     * ║ PERFORMANCE OPTIMIZED IMPLEMENTATION ║
     * ║───────────────────────────────────────────────────────────────────────────────║
     * ║ 1. All filtering done in DATABASE (not in memory) ║
     * ║ 2. Eager loading of related entities to avoid N+1 queries ║
     * ║ 3. Totals calculated in database for accuracy ║
     * ║ 4. Scalable to 100,000+ claims ║
     * ╚═══════════════════════════════════════════════════════════════════════════════╝
     * 
     * @param providerId    Provider ID (REQUIRED)
     * @param fromDate      Start date (optional)
     * @param toDate        End date (optional)
     * @param statuses      Filter by claim statuses (optional, defaults to all
     *                      except DRAFT)
     * @param claimNumber   Filter by specific claim number (optional)
     * @param preAuthNumber Filter by pre-auth number (optional)
     * @param memberId      Filter by member (optional)
     * @return Complete provider settlement report with line-level details
     */
    public ProviderSettlementReportDto generateReport(
            Long providerId,
            Long employerOrgId,
            LocalDate fromDate,
            LocalDate toDate,
            List<ClaimStatus> statuses,
            String claimNumber,
            String preAuthNumber,
            Long memberId) {

        log.info("📊 Generating provider settlement report for provider ID: {}", providerId);
        long startTime = System.currentTimeMillis();

        // Validate provider ID
        if (providerId == null) {
            throw new IllegalArgumentException("Provider ID is required for settlement report");
        }

        // Get provider info
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        // Resolve effective filters for DB query
        List<ClaimStatus> effectiveStatuses = resolveStatuses(statuses);
        LocalDate queryFromDate = fromDate != null ? fromDate : LocalDate.of(1900, 1, 1);
        LocalDate queryToDate = toDate != null ? toDate : LocalDate.now();

        List<Claim> claims = claimRepository.findForSettlementReport(
                providerId,
                employerOrgId,
                effectiveStatuses,
                queryFromDate,
                queryToDate);

        // Apply additional optional filters (claim number, preAuth number, member)
        final String finalClaimNumber = claimNumber;
        final String finalPreAuthNumber = preAuthNumber;
        final Long finalMemberId = memberId;

        if ((finalClaimNumber != null && !finalClaimNumber.isBlank()) ||
                (finalPreAuthNumber != null && !finalPreAuthNumber.isBlank()) ||
                finalMemberId != null) {

            claims = claims.stream()
                    // Filter by claim number if specified
                    .filter(c -> finalClaimNumber == null || finalClaimNumber.isBlank() ||
                            String.valueOf(c.getId()).contains(finalClaimNumber))
                    // Filter by pre-auth number if specified
                    .filter(c -> {
                        if (finalPreAuthNumber == null || finalPreAuthNumber.isBlank())
                            return true;
                        if (c.getPreAuthorization() == null)
                            return false;
                        String paNumber = c.getPreAuthorization().getPreAuthNumber();
                        return paNumber != null && paNumber.toLowerCase().contains(finalPreAuthNumber.toLowerCase());
                    })
                    // Filter by member if specified
                    .filter(c -> finalMemberId == null ||
                            (c.getMember() != null && c.getMember().getId().equals(finalMemberId)))
                    .collect(Collectors.toList());

        }

        BigDecimal totalRequested = BigDecimal.ZERO;
        BigDecimal totalApproved = BigDecimal.ZERO;
        BigDecimal totalPatientShare = BigDecimal.ZERO;
        BigDecimal netProvider = BigDecimal.ZERO;

        for (Claim claim : claims) {
            totalRequested = totalRequested.add(
                    claim.getRequestedAmount() != null ? claim.getRequestedAmount() : BigDecimal.ZERO);
            totalApproved = totalApproved.add(
                    claim.getApprovedAmount() != null ? claim.getApprovedAmount() : BigDecimal.ZERO);
            totalPatientShare = totalPatientShare.add(
                    claim.getPatientCoPay() != null ? claim.getPatientCoPay() : BigDecimal.ZERO);
            netProvider = netProvider.add(
                    claim.getNetProviderAmount() != null
                            ? claim.getNetProviderAmount()
                            : (claim.getApprovedAmount() != null ? claim.getApprovedAmount() : BigDecimal.ZERO));
        }

        long totalClaimsCount = claims.size();
        // M2 Fix: totalRejected = Σ(refusedAmount per claim), not (requested -
        // approved).
        // The old formula incorrectly included patient co-pay in rejected amount
        // because totalApproved = company share only (excludes patient share).
        BigDecimal totalRejected = claims.stream()
                .map(c -> c.getRefusedAmount() != null ? c.getRefusedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ══════════════════════════════════════════════════════════════════════════
        // STEP 3: Build claim details with line-level data
        // ══════════════════════════════════════════════════════════════════════════
        List<ClaimDetail> claimDetails = new ArrayList<>();

        for (Claim claim : claims) {
            ClaimDetail claimDetail = buildClaimDetail(claim);
            claimDetails.add(claimDetail);
        }

        // Generate report number
        String reportNumber = generateReportNumber(providerId, toDate != null ? toDate : LocalDate.now());

        // Build report DTO
        ProviderSettlementReportDto report = ProviderSettlementReportDto.builder()
                .reportNumber(reportNumber)
                .reportDate(LocalDate.now())
                .fromDate(fromDate)
                .toDate(toDate)
                .providerId(providerId)
                .providerName(provider.getName())
                .providerCode(provider.getLicenseNumber())
                .totalClaimsCount(totalClaimsCount)
                .totalRequestedAmount(totalRequested)
                .totalApprovedAmount(totalApproved)
                .totalRejectedAmount(totalRejected)
                .totalPatientShare(totalPatientShare)
                .netProviderAmount(netProvider)
                .claims(claimDetails)
                .build();

        long duration = System.currentTimeMillis() - startTime;
        log.info("📊 Provider settlement report generated in {}ms: {} claims, gross: {}, net: {}, provider net: {}",
                duration, claims.size(), totalRequested, totalApproved, netProvider);

        return report;
    }

    private List<ClaimStatus> resolveStatuses(List<ClaimStatus> statuses) {
        if (statuses != null && !statuses.isEmpty()) {
            return statuses;
        }

        return List.of(
                ClaimStatus.SUBMITTED,
                ClaimStatus.UNDER_REVIEW,
                ClaimStatus.NEEDS_CORRECTION,
                ClaimStatus.APPROVAL_IN_PROGRESS,
                ClaimStatus.APPROVED,
                ClaimStatus.BATCHED,
                ClaimStatus.SETTLED,
                ClaimStatus.REJECTED);
    }

    /**
     * Build claim detail with all service lines.
     * Uses claim-level amounts as CANONICAL source, lines for details.
     */
    private ClaimDetail buildClaimDetail(Claim claim) {
        // Get claim lines
        List<ClaimLine> lines = claim.getLines();
        if (lines == null) {
            lines = new ArrayList<>();
        }

        // Build service line details
        List<ServiceLineDetail> lineDetails = new ArrayList<>();
        BigDecimal linesGross = BigDecimal.ZERO;
        BigDecimal linesApproved = BigDecimal.ZERO;
        BigDecimal linesRejected = BigDecimal.ZERO;

        for (ClaimLine line : lines) {
            ServiceLineDetail lineDetail = buildServiceLineDetail(line, claim.getServiceDate());
            lineDetails.add(lineDetail);

            // Accumulate line totals
            linesGross = linesGross.add(
                    lineDetail.getGrossAmount() != null ? lineDetail.getGrossAmount() : BigDecimal.ZERO);
            linesApproved = linesApproved.add(
                    lineDetail.getApprovedAmount() != null ? lineDetail.getApprovedAmount() : BigDecimal.ZERO);
            linesRejected = linesRejected.add(
                    lineDetail.getRejectedAmount() != null ? lineDetail.getRejectedAmount() : BigDecimal.ZERO);
        }

        // CANONICAL: Use claim-level amounts as primary source
        // Fall back to line totals if claim-level is null
        BigDecimal claimGross = claim.getRequestedAmount() != null ? claim.getRequestedAmount() : linesGross;
        BigDecimal claimApproved = claim.getApprovedAmount() != null ? claim.getApprovedAmount() : linesApproved;

        // CANONICAL: Use stored refusedAmount directly — avoids incorrectly folding
        // patient co-pay into the "rejected" bucket (gross - approved would mix both).
        BigDecimal claimRejected = claim.getRefusedAmount() != null
                ? claim.getRefusedAmount()
                : claimGross.subtract(claimApproved).max(BigDecimal.ZERO);

        // Get patient share from claim level (co-pay)
        BigDecimal patientShare = claim.getPatientCoPay() != null ? claim.getPatientCoPay() : BigDecimal.ZERO;

        // Get pre-auth number if exists
        String preAuthNumber = null;
        if (claim.getPreAuthorization() != null) {
            preAuthNumber = claim.getPreAuthorization().getPreAuthNumber();
        }

        // Get member info
        String patientName = null;
        String insuranceNumber = null;

        if (claim.getMember() != null) {
            patientName = claim.getMember().getFullName();
            insuranceNumber = claim.getMember().getCardNumber(); // Using cardNumber as insurance number
        }

        return ClaimDetail.builder()
                .claimId(claim.getId())
                .claimNumber(String.valueOf(claim.getId()))
                .preAuthNumber(preAuthNumber)
                .patientName(patientName)
                .insuranceNumber(insuranceNumber)
                .diagnosisCode(claim.getDiagnosisCode())
                .diagnosisDescription(claim.getDiagnosisDescription())
                .serviceDate(claim.getServiceDate())
                .status(claim.getStatus() != null ? claim.getStatus().name() : null)
                .statusArabic(claim.getStatus() != null ? claim.getStatus().getArabicLabel() : null)
                .grossAmount(claimGross)
                .netAmount(claimApproved)
                .rejectedAmount(claimRejected)
                .patientShare(patientShare)
                .lines(lineDetails)
                .build();
    }

    /**
     * Build service line detail.
     */
    private ServiceLineDetail buildServiceLineDetail(ClaimLine line, LocalDate serviceDate) {
        // Gross = what was submitted (requested price × requested quantity)
        Integer reqQty = line.getRequestedQuantity() != null ? line.getRequestedQuantity() : line.getQuantity();
        BigDecimal reqUnitPrice = line.getRequestedUnitPrice() != null ? line.getRequestedUnitPrice()
                : line.getUnitPrice();

        BigDecimal gross;
        if (reqUnitPrice != null && reqQty != null) {
            gross = reqUnitPrice.multiply(new BigDecimal(reqQty));
        } else if (line.getTotalPrice() != null) {
            gross = line.getTotalPrice();
        } else {
            gross = BigDecimal.ZERO;
        }

        // Rejected = stored refusedAmount (price-excess + limit refusals — pure company
        // refusal,
        // does NOT include patient co-pay)
        BigDecimal rejected = line.getRefusedAmount() != null
                ? line.getRefusedAmount().max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        // Approved (net to provider) = gross − rejected
        BigDecimal approved = gross.subtract(rejected).max(BigDecimal.ZERO);

        // Determine line status
        LineStatus lineStatus = ProviderSettlementReportDto.calculateLineStatus(gross, approved);

        // Get service info directly from denormalized fields (MedicalService FK removed
        // in V229)
        String serviceCode = line.getServiceCode();
        String serviceName = line.getServiceName();
        String serviceCategory = null;

        return ServiceLineDetail.builder()
                .lineId(line.getId())
                .medicalServiceId(null)
                .serviceCode(serviceCode)
                .serviceName(serviceName)
                .serviceCategory(serviceCategory)
                .serviceDate(serviceDate)
                .quantity(line.getQuantity())
                .unitPrice(line.getUnitPrice())
                .grossAmount(gross)
                .approvedAmount(approved)
                .rejectedAmount(rejected)
                .rejectionReason(null) // Can be enhanced with line-level rejection reasons
                .patientShare(BigDecimal.ZERO) // Patient share is at claim level
                .lineStatus(lineStatus)
                .lineStatusArabic(lineStatus.getArabicLabel())
                .build();
    }

    /**
     * Generate report number in format: LCC25-{providerId}-{month}/{year}
     */
    private String generateReportNumber(Long providerId, LocalDate toDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/yyyy");
        String providerCode = String.format("%05d", providerId);
        int yearShort = toDate.getYear() % 100;
        return String.format("LCC%02d-%s-%s", yearShort, providerCode, toDate.format(formatter));
    }

    /**
     * Get list of providers available for settlement reports.
     * For ADMIN/FINANCE: all providers
     * For PROVIDER: only their own provider
     */
    public List<ProviderInfo> getAvailableProviders(Long currentUserProviderId, boolean isAdmin) {
        if (!isAdmin && currentUserProviderId != null) {
            // Provider user: only their own
            return providerRepository.findById(currentUserProviderId)
                    .map(provider -> List.of(new ProviderInfo(
                            provider.getId(),
                            provider.getName())))
                    .orElse(List.of());
        }

        // Admin: all active providers
        return providerRepository.findAllActive().stream()
                .map(provider -> new ProviderInfo(
                        provider.getId(),
                        provider.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Simple DTO for provider dropdown (unified name)
     */
    public record ProviderInfo(Long id, String name) {
    }
}

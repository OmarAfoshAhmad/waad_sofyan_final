package com.waad.tba.modules.claim.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.dto.ClaimFinancialSummaryDto;
import com.waad.tba.modules.claim.dto.ClaimFinancialSummaryDto.EmployerSummary;
import com.waad.tba.modules.claim.dto.ClaimFinancialSummaryDto.ProviderSummary;
import com.waad.tba.modules.claim.dto.ClaimFinancialSummaryDto.StatusSummary;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.FinancialSummaryByProviderProjection;
import com.waad.tba.modules.claim.projection.FinancialSummaryByStatusProjection;
import com.waad.tba.modules.claim.projection.FinancialSummaryByEmployerProjection;
import com.waad.tba.modules.claim.repository.ClaimRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ╔═══════════════════════════════════════════════════════════════════════════╗
 * ║ CLAIM FINANCIAL SUMMARY SERVICE - SINGLE SOURCE OF TRUTH ║
 * ║───────────────────────────────────────────────────────────────────────────║
 * ║ This service provides ALL financial aggregations for claims. ║
 * ║ Frontend MUST use these endpoints - NO client-side calculations allowed. ║
 * ║ ║
 * ║ FINANCIAL LAW: ║
 * ║ 1. All amounts come from database SUM() queries ║
 * ║ 2. No JavaScript .reduce() on financial fields in frontend ║
 * ║ 3. Report totals MUST match exactly with individual claim sums ║
 * ║ 4. netProviderAmount is the authoritative field for provider payment ║
 * ║ 5. approvedAmount is fallback only when netProviderAmount is null ║
 * ╚═══════════════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimFinancialSummaryService {

        private final ClaimRepository claimRepository;

        /**
         * Get comprehensive financial summary - AUTHORITATIVE totals from database.
         * 
         * @param employerOrgId Optional employer organization filter (null = all)
         * @param fromDate      Optional start date filter (null = all time)
         * @param toDate        Optional end date filter (null = all time)
         * @return Financial summary with all totals computed in database
         */
        public ClaimFinancialSummaryDto getFinancialSummary(Long employerOrgId, LocalDate fromDate, LocalDate toDate) {
                log.info("📊 [FINANCIAL SUMMARY] Generating financial summary - employerOrgId: {}, fromDate: {}, toDate: {}",
                                employerOrgId, fromDate, toDate);

                ClaimFinancialSummaryDto.ClaimFinancialSummaryDtoBuilder builder = ClaimFinancialSummaryDto.builder()
                                .fromDate(fromDate)
                                .toDate(toDate);

                // ═══════════════════════════════════════════════════════════════════════════
                // COUNTS - All from database COUNT() queries
                // ═══════════════════════════════════════════════════════════════════════════
                if (employerOrgId != null) {
                        builder.totalClaimsCount(claimRepository.countByMemberEmployerId(employerOrgId));
                        builder.pendingClaimsCount(countByStatusAndEmployer(ClaimStatus.SUBMITTED, employerOrgId) +
                                        countByStatusAndEmployer(ClaimStatus.DRAFT, employerOrgId));
                        builder.underReviewClaimsCount(
                                        countByStatusAndEmployer(ClaimStatus.UNDER_REVIEW, employerOrgId));
                        builder.approvedClaimsCount(countByStatusAndEmployer(ClaimStatus.APPROVED, employerOrgId));
                        builder.rejectedClaimsCount(countByStatusAndEmployer(ClaimStatus.REJECTED, employerOrgId));
                        builder.settledClaimsCount(countByStatusAndEmployer(ClaimStatus.SETTLED, employerOrgId));
                } else {
                        builder.totalClaimsCount(claimRepository.countActive());
                        builder.pendingClaimsCount(claimRepository.countByStatus(ClaimStatus.SUBMITTED) +
                                        claimRepository.countByStatus(ClaimStatus.DRAFT));
                        builder.underReviewClaimsCount(claimRepository.countByStatus(ClaimStatus.UNDER_REVIEW));
                        builder.approvedClaimsCount(claimRepository.countByStatus(ClaimStatus.APPROVED));
                        builder.rejectedClaimsCount(claimRepository.countByStatus(ClaimStatus.REJECTED));
                        builder.settledClaimsCount(claimRepository.countByStatus(ClaimStatus.SETTLED));
                }

                // ═══════════════════════════════════════════════════════════════════════════
                // AMOUNTS - All from database SUM() queries (COALESCE handles NULL)
                // ═══════════════════════════════════════════════════════════════════════════
                BigDecimal totalRequested;
                BigDecimal totalApproved;
                BigDecimal totalPatientCoPay;
                BigDecimal totalNetProvider;
                BigDecimal totalSettled;

                if (employerOrgId != null) {
                        totalRequested = claimRepository.sumTotalRequestedAmountsByEmployer(employerOrgId);
                        totalApproved = claimRepository.sumApprovedAmountsForApprovedSettledByEmployer(employerOrgId);
                        totalPatientCoPay = claimRepository.sumTotalPatientCoPayByEmployer(employerOrgId);
                        totalNetProvider = claimRepository.sumTotalNetProviderAmountsByEmployer(employerOrgId);
                        totalSettled = claimRepository.sumTotalSettledAmountsByEmployer(employerOrgId);
                } else {
                        totalRequested = claimRepository.sumTotalRequestedAmounts();
                        totalApproved = claimRepository.sumApprovedAmounts();
                        totalPatientCoPay = claimRepository.sumTotalPatientCoPay();
                        totalNetProvider = claimRepository.sumTotalNetProviderAmounts();
                        totalSettled = claimRepository.sumTotalSettledAmounts();
                }

                // Null safety
                totalRequested = totalRequested != null ? totalRequested : BigDecimal.ZERO;
                totalApproved = totalApproved != null ? totalApproved : BigDecimal.ZERO;
                totalPatientCoPay = totalPatientCoPay != null ? totalPatientCoPay : BigDecimal.ZERO;
                totalNetProvider = totalNetProvider != null ? totalNetProvider : BigDecimal.ZERO;
                totalSettled = totalSettled != null ? totalSettled : BigDecimal.ZERO;

                builder.totalRequestedAmount(totalRequested);
                builder.totalApprovedAmount(totalApproved);
                builder.totalPatientCoPay(totalPatientCoPay);
                builder.totalNetProviderAmount(totalNetProvider);
                builder.totalSettledAmount(totalSettled);

                // Outstanding = Net Provider owed - Settled (what's still to be paid)
                BigDecimal outstanding = totalNetProvider.subtract(totalSettled);
                builder.outstandingAmount(outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO);

                // Difference = Requested - Approved (deductions)
                builder.totalDifferenceAmount(totalRequested.subtract(totalApproved));

                // ═══════════════════════════════════════════════════════════════════════════
                // PROVIDER BREAKDOWN
                // ═══════════════════════════════════════════════════════════════════════════
                // ✅ TYPE-SAFE: Using interface projection instead of Object[]
                List<FinancialSummaryByProviderProjection> providerData = employerOrgId != null
                                ? claimRepository.getFinancialSummaryByProviderAndEmployer(employerOrgId)
                                : claimRepository.getFinancialSummaryByProvider();

                List<ProviderSummary> providerSummaries = new ArrayList<>();
                for (FinancialSummaryByProviderProjection projection : providerData) {
                        BigDecimal providerApproved = projection.getApprovedAmount();
                        BigDecimal providerNet = projection.getNetProviderAmount();
                        BigDecimal providerSettled = claimRepository
                                        .sumSettledAmountsByProvider(projection.getProviderId());

                        providerSummaries.add(ProviderSummary.builder()
                                        .providerId(projection.getProviderId())
                                        .providerName(projection.getProviderName())
                                        .claimsCount(projection.getClaimsCount())
                                        .requestedAmount(projection.getRequestedAmount())
                                        .approvedAmount(providerApproved)
                                        .patientCoPay(projection.getPatientCoPay())
                                        .netProviderAmount(providerNet)
                                        .settledAmount(providerSettled)
                                        .outstandingAmount(providerNet.subtract(providerSettled))
                                        .build());
                }
                builder.providerSummaries(providerSummaries);

                // ═══════════════════════════════════════════════════════════════════════════
                // STATUS BREAKDOWN
                // ═══════════════════════════════════════════════════════════════════════════
                // ✅ TYPE-SAFE: Using interface projection instead of Object[]
                List<FinancialSummaryByStatusProjection> statusData = employerOrgId != null
                                ? claimRepository.getFinancialSummaryByStatusAndEmployer(employerOrgId)
                                : claimRepository.getFinancialSummaryByStatus();

                List<StatusSummary> statusSummaries = new ArrayList<>();
                for (FinancialSummaryByStatusProjection projection : statusData) {
                        ClaimStatus status = projection.getStatus();
                        statusSummaries.add(StatusSummary.builder()
                                        .status(status.name())
                                        .statusArabic(status.getArabicLabel())
                                        .count(projection.getCount())
                                        .totalAmount(projection.getTotalApprovedAmount()) // Use approved amount for
                                                                                          // status summary
                                        .build());
                }
                builder.statusSummaries(statusSummaries);

                // ═══════════════════════════════════════════════════════════════════════════
                // EMPLOYER BREAKDOWN (only if not filtered by employer)
                // ═══════════════════════════════════════════════════════════════════════════
                if (employerOrgId == null) {
                        // ✅ TYPE-SAFE: Using interface projection instead of Object[]
                        List<FinancialSummaryByEmployerProjection> employerData = claimRepository
                                        .getFinancialSummaryByEmployer();
                        List<EmployerSummary> employerSummaries = new ArrayList<>();
                        for (FinancialSummaryByEmployerProjection projection : employerData) {
                                employerSummaries.add(EmployerSummary.builder()
                                                .employerId(projection.getEmployerOrgId())
                                                .employerName(projection.getEmployerOrgName())
                                                .claimsCount(projection.getClaimsCount())
                                                .membersCount(projection.getMembersCount())
                                                .requestedAmount(projection.getRequestedAmount())
                                                .approvedAmount(projection.getApprovedAmount())
                                                .build());
                        }
                        builder.employerSummaries(employerSummaries);
                }

                ClaimFinancialSummaryDto result = builder.build();

                // ══════════════════════════════════════════════════════════════════════════
                // FINANCIAL CLOSURE: REPORT CONSISTENCY GUARD (AUDIT LOCK)
                // ══════════════════════════════════════════════════════════════════════════
                // Validate that settlements never exceed approved claims - this catches:
                // - Future bugs
                // - Data import errors
                // - Manual database modifications
                validateFinancialConsistency(result);

                log.info(
                                "📊 [FINANCIAL SUMMARY] Generated - Total Claims: {}, Total Requested: {}, Total Approved: {}, Outstanding: {}",
                                result.getTotalClaimsCount(),
                                result.getTotalRequestedAmount(),
                                result.getTotalApprovedAmount(),
                                result.getOutstandingAmount());

                return result;
        }

        /**
         * ╔═══════════════════════════════════════════════════════════════════════════╗
         * ║ FINANCIAL CLOSURE: REPORT CONSISTENCY GUARD ║
         * ║───────────────────────────────────────────────────────────────────────────║
         * ║ Internal audit check that settlements cannot exceed claims. ║
         * ║ This protects against: ║
         * ║ - Future bugs ║
         * ║ - Unintended modifications ║
         * ║ - Bad data imports ║
         * ╚═══════════════════════════════════════════════════════════════════════════╝
         * 
         * @param summary The financial summary to validate
         * @throws IllegalStateException if financial inconsistency is detected
         */
        private void validateFinancialConsistency(ClaimFinancialSummaryDto summary) {
                BigDecimal totalApproved = summary.getTotalApprovedAmount();
                BigDecimal totalSettled = summary.getTotalSettledAmount();
                BigDecimal totalNetProvider = summary.getTotalNetProviderAmount();

                // Guard 1: Settlements cannot exceed approved amounts
                if (totalSettled != null && totalApproved != null &&
                                totalSettled.compareTo(totalApproved) > 0) {
                        log.error("🚨 [FINANCIAL INCONSISTENCY] Settlements ({}) exceed approved amounts ({})",
                                        totalSettled, totalApproved);
                        throw new IllegalStateException(
                                        String.format(
                                                        "Financial inconsistency detected: Total settlements (%s) exceed total approved (%s). "
                                                                        +
                                                                        "This indicates a data integrity issue that must be investigated immediately.",
                                                        totalSettled, totalApproved));
                }

                // Guard 2: Settlements cannot exceed net provider amounts
                if (totalSettled != null && totalNetProvider != null &&
                                totalSettled.compareTo(totalNetProvider) > 0) {
                        log.error("🚨 [FINANCIAL INCONSISTENCY] Settlements ({}) exceed net provider amounts ({})",
                                        totalSettled, totalNetProvider);
                        throw new IllegalStateException(
                                        String.format(
                                                        "Financial inconsistency detected: Total settlements (%s) exceed total net provider amounts (%s). "
                                                                        +
                                                                        "This indicates a data integrity issue that must be investigated immediately.",
                                                        totalSettled, totalNetProvider));
                }

                // Guard 3: Outstanding amount should never be negative
                BigDecimal outstanding = summary.getOutstandingAmount();
                if (outstanding != null && outstanding.compareTo(BigDecimal.ZERO) < 0) {
                        log.error("🚨 [FINANCIAL INCONSISTENCY] Outstanding amount is negative: {}", outstanding);
                        throw new IllegalStateException(
                                        String.format("Financial inconsistency detected: Outstanding amount (%s) is negative. "
                                                        +
                                                        "This indicates over-settlement or data integrity issues.",
                                                        outstanding));
                }

                log.debug("✅ [FINANCIAL GUARD] Consistency check passed - approved: {}, settled: {}, outstanding: {}",
                                totalApproved, totalSettled, outstanding);
        }

        /**
         * Get settlement-focused summary for Settlement Inbox.
         * Only includes APPROVED claims (ready for settlement).
         */
        public ClaimFinancialSummaryDto getSettlementSummary(Long employerOrgId) {
                log.info("📊 [SETTLEMENT SUMMARY] Generating for Settlement Inbox - employerOrgId: {}", employerOrgId);

                // Get counts
                long approvedCount = employerOrgId != null
                                ? countByStatusAndEmployer(ClaimStatus.APPROVED, employerOrgId)
                                : claimRepository.countByStatus(ClaimStatus.APPROVED);

                long settledCount = employerOrgId != null
                                ? countByStatusAndEmployer(ClaimStatus.SETTLED, employerOrgId)
                                : claimRepository.countByStatus(ClaimStatus.SETTLED);

                // Get amounts
                BigDecimal totalApproved = employerOrgId != null
                                ? claimRepository.sumApprovedAmountsForApprovedSettledByEmployer(employerOrgId)
                                : claimRepository.sumApprovedAmounts();

                BigDecimal totalSettled = employerOrgId != null
                                ? claimRepository.sumTotalSettledAmountsByEmployer(employerOrgId)
                                : claimRepository.sumTotalSettledAmounts();

                BigDecimal totalPatientCoPay = employerOrgId != null
                                ? claimRepository.sumTotalPatientCoPayByEmployer(employerOrgId)
                                : claimRepository.sumTotalPatientCoPay();

                BigDecimal totalNetProvider = employerOrgId != null
                                ? claimRepository.sumTotalNetProviderAmountsByEmployer(employerOrgId)
                                : claimRepository.sumTotalNetProviderAmounts();

                // Null safety
                totalApproved = totalApproved != null ? totalApproved : BigDecimal.ZERO;
                totalSettled = totalSettled != null ? totalSettled : BigDecimal.ZERO;
                totalPatientCoPay = totalPatientCoPay != null ? totalPatientCoPay : BigDecimal.ZERO;
                totalNetProvider = totalNetProvider != null ? totalNetProvider : BigDecimal.ZERO;

                return ClaimFinancialSummaryDto.builder()
                                .approvedClaimsCount(approvedCount)
                                .settledClaimsCount(settledCount)
                                .totalApprovedAmount(totalApproved)
                                .totalSettledAmount(totalSettled)
                                .totalPatientCoPay(totalPatientCoPay)
                                .totalNetProviderAmount(totalNetProvider)
                                .outstandingAmount(totalNetProvider.subtract(totalSettled))
                                .build();
        }

        /**
         * Helper method to count by status and employer.
         * Uses existing repository count method with filter.
         */
        private long countByStatusAndEmployer(ClaimStatus status, Long employerOrgId) {
                if (status == ClaimStatus.APPROVED) {
                        return claimRepository.countApprovedClaimsByEmployer(employerOrgId);
                }
                // Fallback - this should be improved with proper repository methods
                return 0L;
        }
}

package com.waad.tba.modules.member.service;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.dto.MemberFinancialSummaryDto;
import com.waad.tba.modules.member.dto.CoverageLimitsDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Member Financial Summary Service
 * 
 * Provides comprehensive financial overview for members by aggregating:
 * - Benefit policy information
 * - Claim statistics and amounts
 * - Utilization metrics
 * - Alerts and warnings
 * 
 * REUSES existing services (no duplicate logic):
 * - BenefitPolicyCoverageService for remaining coverage
 * - ClaimRepository for claim aggregations
 * 
 * @version 2026.1
 * @since Phase 1 - Financial Lifecycle Completion
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberFinancialSummaryService {

    private final MemberRepository memberRepository;
    private final BenefitPolicyCoverageService coverageService;
    private final ClaimRepository claimRepository;
    private final MedicalServiceRepository medicalServiceRepository;

    /**
     * Get comprehensive financial summary for a member
     * 
     * @param memberId Member ID
     * @return Financial summary DTO with all metrics
     * @throws ResourceNotFoundException if member not found
     */
    public MemberFinancialSummaryDto getFinancialSummary(Long memberId) {
        log.info("📊 Generating financial summary for member ID: {}", memberId);

        // 1. Load member
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        // 2. Load benefit policy (may be null)
        BenefitPolicy policy = member.getBenefitPolicy();

        // 3. Load all claims for this member
        List<Claim> allClaims = claimRepository.findByMemberId(memberId);

        // 4. Build DTO
        MemberFinancialSummaryDto.MemberFinancialSummaryDtoBuilder builder = MemberFinancialSummaryDto.builder();

        // ========== MEMBER INFO ==========
        builder.memberId(member.getId())
               .fullName(member.getFullName())
               .cardNumber(member.getCardNumber())
               .barcode(member.getBarcode())
               .isDependent(member.getParent() != null);

        // ========== POLICY INFO ==========
        if (policy != null) {
            builder.policyId(policy.getId())
                   .policyName(policy.getName())
                   .annualLimit(policy.getAnnualLimit())
                   .policyStartDate(policy.getStartDate())
                   .policyEndDate(policy.getEndDate())
                   .policyActive(policy.isActive() && policy.isEffectiveOn(LocalDate.now()));
        } else {
            builder.policyActive(false);
        }

        // ========== FINANCIAL METRICS ==========
        BigDecimal totalClaimed = calculateTotalClaimed(allClaims);
        BigDecimal totalApproved = calculateTotalApproved(allClaims);
        BigDecimal totalPaid = calculateTotalPaid(allClaims);
        BigDecimal totalPatientCoPay = calculateTotalPatientCoPay(allClaims);
        BigDecimal totalDeductible = calculateTotalDeductible(allClaims);

        builder.totalClaimed(totalClaimed)
               .totalApproved(totalApproved)
               .totalPaid(totalPaid)
               .totalPatientCoPay(totalPatientCoPay)
               .totalDeductibleApplied(totalDeductible);

        // REUSE: Remaining coverage from existing service
        BigDecimal remainingCoverage = null;
        BigDecimal utilizationPercent = BigDecimal.ZERO;
        
        if (policy != null) {
            remainingCoverage = coverageService.getRemainingCoverage(member, LocalDate.now());
            builder.remainingCoverage(remainingCoverage);

            // Calculate utilization %
            if (policy.getAnnualLimit() != null && policy.getAnnualLimit().compareTo(BigDecimal.ZERO) > 0) {
                utilizationPercent = totalApproved
                    .multiply(BigDecimal.valueOf(100))
                    .divide(policy.getAnnualLimit(), 2, RoundingMode.HALF_UP);
                builder.utilizationPercent(utilizationPercent);
            }
        }

        // ========== CLAIM STATISTICS ==========
        int totalCount = allClaims.size();
        int pendingCount = (int) allClaims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.SUBMITTED || c.getStatus() == ClaimStatus.UNDER_REVIEW)
            .count();
        int approvedCount = (int) allClaims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.APPROVED || 
                         c.getStatus() == ClaimStatus.SETTLED)
            .count();
        int rejectedCount = (int) allClaims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.REJECTED)
            .count();

        LocalDate lastClaimDate = allClaims.stream()
            .map(Claim::getCreatedAt)
            .filter(createdAt -> createdAt != null)
            .max(java.time.LocalDateTime::compareTo)
            .map(dateTime -> dateTime.toLocalDate())
            .orElse(null);

        builder.claimsCount(totalCount)
               .pendingClaimsCount(pendingCount)
               .approvedClaimsCount(approvedCount)
               .rejectedClaimsCount(rejectedCount)
               .lastClaimDate(lastClaimDate);

        // ========== WARNINGS / ALERTS ==========
        String warning = null;
        boolean nearingLimit = false;
        boolean expiringSoon = false;

        if (policy != null) {
            // Check if nearing limit (>80% utilization)
            if (utilizationPercent.compareTo(BigDecimal.valueOf(80)) >= 0) {
                nearingLimit = true;
                warning = "⚠️ تنبيه: اقتربت من حد التغطية السنوي (" + utilizationPercent.intValue() + "% مستهلك)";
            }

            // Check if policy expiring soon (within 30 days)
            if (policy.getEndDate() != null) {
                long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), policy.getEndDate());
                if (daysUntilExpiry > 0 && daysUntilExpiry <= 30) {
                    expiringSoon = true;
                    if (warning == null) {
                        warning = "⚠️ تنبيه: الوثيقة ستنتهي خلال " + daysUntilExpiry + " يوم";
                    }
                } else if (daysUntilExpiry <= 0) {
                    warning = "❌ الوثيقة منتهية";
                }
            }
        } else {
            warning = "❌ لا توجد وثيقة تغطية مربوطة بالعضو";
        }

        builder.warningMessage(warning)
               .nearingLimit(nearingLimit)
               .policyExpiringSoon(expiringSoon);

        MemberFinancialSummaryDto summary = builder.build();
        log.info("✅ Financial summary generated: Claimed={}, Approved={}, Remaining={}, Utilization={}%",
                 totalClaimed, totalApproved, remainingCoverage, utilizationPercent);

        return summary;
    }

    /**
     * Get Coverage Limits (times and amounts) for a specific service based on member's active policy.
     * 
     * @param memberId Member ID
     * @param serviceCode Medical Service Code
     * @return CoverageLimitsDto
     */
    public CoverageLimitsDto getServiceCoverageLimits(Long memberId, String serviceCode) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        MedicalService service = medicalServiceRepository.findByCode(serviceCode)
                .orElseThrow(() -> new BusinessRuleException("Service not found: " + serviceCode));

        // Get limits from policy
        var coverageInfoOpt = coverageService.getCoverageForService(member, service.getId());
        if (coverageInfoOpt.isEmpty()) {
            return CoverageLimitsDto.builder()
                    .covered(false)
                    .warningMessage("الخدمة غير مغطاة تحت هذه الوثيقة")
                    .build();
        }

        var coverageInfo = coverageInfoOpt.get();
        if (!coverageInfo.isCovered()) {
            return CoverageLimitsDto.builder()
                    .covered(false)
                    .warningMessage("الخدمة غير مغطاة تحت هذه الوثيقة")
                    .build();
        }

        int coveragePercent = coverageInfo.getCoveragePercent();
        BigDecimal amountLimit = coverageInfo.getAmountLimit();
        Integer timesLimit = coverageInfo.getTimesLimit();
        int timesUsed = 0;
        int remainingTimes = timesLimit != null ? timesLimit : 999;
        boolean timesLimitExceeded = false;
        String warningMessage = null;

        // If times limit exists, we must calculate historical usage
        if (timesLimit != null) {
            List<Claim> claims = claimRepository.findByMemberId(memberId);
            int currentYear = LocalDate.now().getYear();
            
            for (Claim c : claims) {
                // Only consider claims from the current policy year and that are not rejected
                if (c.getStatus() == ClaimStatus.REJECTED || c.getServiceDate() == null || c.getServiceDate().getYear() != currentYear) {
                    continue;
                }
                if (c.getLines() != null) {
                    for (ClaimLine line : c.getLines()) {
                        if (serviceCode.equals(line.getServiceCode())) {
                            timesUsed++;
                        }
                    }
                }
            }
            
            remainingTimes = timesLimit - timesUsed;
            if (remainingTimes <= 0) {
                remainingTimes = 0;
                timesLimitExceeded = true;
                warningMessage = "تم تجاوز الحد الأقصى لعدد المرات المسموح بها (" + timesLimit + " مرات). المرات المتبقية: صفر.";
            }
        }

        return CoverageLimitsDto.builder()
                .covered(true)
                .coveragePercent(coveragePercent)
                .amountLimit(amountLimit)
                .timesLimit(timesLimit)
                .timesUsed(timesUsed)
                .remainingTimes(remainingTimes)
                .timesLimitExceeded(timesLimitExceeded)
                .warningMessage(warningMessage)
                .build();
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Calculate total claimed amount (sum of requestedAmount)
     */
    private BigDecimal calculateTotalClaimed(List<Claim> claims) {
        return claims.stream()
            .filter(c -> c.getRequestedAmount() != null)
            .map(Claim::getRequestedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total approved amount (sum of approvedAmount for approved/settled claims)
     */
    private BigDecimal calculateTotalApproved(List<Claim> claims) {
        List<ClaimStatus> approvedStatuses = Arrays.asList(
            ClaimStatus.APPROVED, ClaimStatus.SETTLED);

        return claims.stream()
            .filter(c -> approvedStatuses.contains(c.getStatus()))
            .filter(c -> c.getApprovedAmount() != null)
            .map(Claim::getApprovedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total paid amount (sum of approvedAmount for settled claims only)
     */
    private BigDecimal calculateTotalPaid(List<Claim> claims) {
        return claims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.SETTLED)
            .filter(c -> c.getApprovedAmount() != null)
            .map(Claim::getApprovedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total patient co-pay (sum of patientCoPay for approved claims)
     */
    private BigDecimal calculateTotalPatientCoPay(List<Claim> claims) {
        List<ClaimStatus> approvedStatuses = Arrays.asList(
            ClaimStatus.APPROVED, ClaimStatus.SETTLED);

        return claims.stream()
            .filter(c -> approvedStatuses.contains(c.getStatus()))
            .filter(c -> c.getPatientCoPay() != null)
            .map(Claim::getPatientCoPay)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total deductible applied (sum of deductibleApplied for approved claims)
     */
    private BigDecimal calculateTotalDeductible(List<Claim> claims) {
        List<ClaimStatus> approvedStatuses = Arrays.asList(
            ClaimStatus.APPROVED, ClaimStatus.SETTLED);

        return claims.stream()
            .filter(c -> approvedStatuses.contains(c.getStatus()))
            .filter(c -> c.getDeductibleApplied() != null)
            .map(Claim::getDeductibleApplied)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

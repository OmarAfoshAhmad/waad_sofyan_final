package com.waad.tba.modules.claim.service;

import com.waad.tba.common.service.BusinessDaysCalculatorService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * SLA Monitoring Scheduler.
 * 
 * Runs daily to check SLA compliance and send alerts for claims approaching
 * deadlines.
 * 
 * Schedule:
 * - Saturday-Thursday at 9:00 AM (Libya time)
 * - Skips Friday (weekend)
 * 
 * Functions:
 * 1. Alert reviewers about claims approaching deadline (within 2 business days)
 * 2. Report overall SLA metrics (compliance rate, average processing time)
 * 3. Log SLA breaches for audit
 * 
 * @since Phase 1 - SLA Implementation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaMonitoringScheduler {

    private final ClaimRepository claimRepository;
    private final BusinessDaysCalculatorService businessDaysCalculator;

    /**
     * Daily SLA compliance check.
     * 
     * Runs Saturday-Thursday at 9:00 AM.
     * Cron: "0 0 9 * * 0-4,6"
     * - Second: 0
     * - Minute: 0
     * - Hour: 9
     * - Day of month: * (any)
     * - Month: * (any)
     * - Day of week: 0-4,6 (Sun-Thu, Sat)
     */
    @Scheduled(cron = "0 0 9 * * 0-4,6", zone = "Africa/Tripoli")
    public void checkSlaCompliance() {
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("🔍 Running SLA compliance check at {}", LocalDate.now());
        log.info("═══════════════════════════════════════════════════════════════");

        try {
            // 1. Check claims approaching deadline
            checkApproachingDeadlines();

            // 2. Report overall SLA metrics
            reportSlaMetrics();

            // 3. Check for data integrity issues
            checkDataIntegrity();

            log.info("✅ SLA compliance check completed successfully");

        } catch (Exception e) {
            log.error("❌ Error during SLA compliance check", e);
        }

        log.info("═══════════════════════════════════════════════════════════════\n");
    }

    /**
     * Check for claims approaching their SLA deadline.
     * Alerts are logged for reviewers to prioritize these claims.
     */
    private void checkApproachingDeadlines() {
        log.info("📅 Checking claims approaching deadline...");

        LocalDate today = LocalDate.now();
        LocalDate twoDaysOut = today.plusDays(2);

        List<Claim> approaching = claimRepository.findClaimsApproachingDeadline(today, twoDaysOut);

        if (approaching.isEmpty()) {
            log.info("✅ No claims approaching deadline");
            return;
        }

        log.warn("⚠️ Found {} claims approaching deadline:", approaching.size());

        for (Claim claim : approaching) {
            if (claim.getExpectedCompletionDate() == null) {
                continue;
            }

            int daysLeft = businessDaysCalculator.calculateBusinessDays(today, claim.getExpectedCompletionDate());

            String urgency = daysLeft == 0 ? "🔴 DUE TODAY"
                    : daysLeft == 1 ? "🟠 DUE TOMORROW" : "🟡 DUE IN " + daysLeft + " DAYS";

            log.warn("  {} Claim ID: {} | Member: {} | Expected: {} | {}",
                    urgency,
                    claim.getId(),
                    claim.getMember() != null ? claim.getMember().getFullName() : "Unknown",
                    claim.getExpectedCompletionDate(),
                    daysLeft + " business days left");
        }

        // Send notifications to reviewers
        // notificationService.sendSlaWarning(approaching);
    }

    /**
     * Report overall SLA metrics.
     */
    private void reportSlaMetrics() {
        log.info("📊 Calculating SLA metrics...");

        try {
            // Compliance rate
            Double complianceRate = claimRepository.getSlaComplianceRate();
            if (complianceRate != null) {
                log.info("  📈 SLA Compliance Rate: {}", String.format("%.2f%%", complianceRate));

                if (complianceRate < 80.0) {
                    log.warn("  ⚠️ Compliance rate below 80%! Immediate action required.");
                } else if (complianceRate < 90.0) {
                    log.warn("  ⚠️ Compliance rate below 90%. Monitor closely.");
                }
            }

            // Average processing time
            Double avgDays = claimRepository.getAverageProcessingDays();
            if (avgDays != null) {
                log.info("  ⏱️  Average Processing Time: {} business days", String.format("%.1f", avgDays));
            }

            // Count by SLA status
            List<Object[]> slaCounts = claimRepository.countBySlStatus();
            int withinSla = 0;
            int exceededSla = 0;

            for (Object[] row : slaCounts) {
                Boolean within = (Boolean) row[0];
                Long count = (Long) row[1];

                if (Boolean.TRUE.equals(within)) {
                    withinSla = count.intValue();
                } else {
                    exceededSla = count.intValue();
                }
            }

            log.info("  ✅ Within SLA: {} claims", withinSla);
            log.info("  ❌ Exceeded SLA: {} claims", exceededSla);

            // Average configured SLA
            Double avgSla = claimRepository.getAverageSlaDaysConfigured();
            if (avgSla != null) {
                log.info("  ⚙️  Average SLA Days Configured: {} days", String.format("%.1f", avgSla));
            }

        } catch (Exception e) {
            log.error("❌ Error calculating SLA metrics", e);
        }
    }

    /**
     * Check for data integrity issues.
     * Identifies claims in UNDER_REVIEW without SLA data (submitted before SLA
     * feature was enabled).
     */
    private void checkDataIntegrity() {
        log.info("🔍 Checking data integrity...");

        List<Claim> withoutSla = claimRepository.findUnderReviewWithoutSla();

        if (withoutSla.isEmpty()) {
            log.info("✅ All UNDER_REVIEW claims have SLA data");
            return;
        }

        log.warn("⚠️ Found {} claims in UNDER_REVIEW without SLA data:", withoutSla.size());

        for (Claim claim : withoutSla) {
            log.warn("  Claim ID: {} | Submitted: {} | Status: {}",
                    claim.getId(),
                    claim.getCreatedAt() != null ? claim.getCreatedAt().toLocalDate() : "Unknown",
                    claim.getStatus());
        }

        log.info("  ℹ️  These claims were submitted before SLA tracking was enabled.");
        log.info("  ℹ️  Consider running a one-time migration to backfill SLA data.");
    }

    /**
     * Get list of claims that exceeded SLA for reporting.
     * Can be called on-demand from admin dashboard.
     */
    public List<Claim> getExceededSlaClaims() {
        return claimRepository.findClaimsExceededSla();
    }

    /**
     * Generate SLA compliance report for a specific date range.
     * Used by admin dashboard.
     */
    public SlaComplianceReport generateComplianceReport() {
        Double complianceRate = claimRepository.getSlaComplianceRate();
        Double avgDays = claimRepository.getAverageProcessingDays();
        List<Object[]> slaCounts = claimRepository.countBySlStatus();

        int withinSla = 0;
        int exceededSla = 0;

        for (Object[] row : slaCounts) {
            Boolean within = (Boolean) row[0];
            Long count = (Long) row[1];

            if (Boolean.TRUE.equals(within)) {
                withinSla = count.intValue();
            } else {
                exceededSla = count.intValue();
            }
        }

        return new SlaComplianceReport(
                complianceRate != null ? complianceRate : 0.0,
                avgDays != null ? avgDays : 0.0,
                withinSla,
                exceededSla,
                withinSla + exceededSla);
    }

    /**
     * SLA Compliance Report DTO.
     */
    public record SlaComplianceReport(
            double complianceRate, // Percentage (0-100)
            double avgProcessingDays, // Average business days
            int claimsWithinSla, // Count of claims within SLA
            int claimsExceededSla, // Count of claims that exceeded SLA
            int totalClaims // Total claims with SLA data
    ) {
        public String getSummary() {
            return String.format(
                    "SLA Compliance: %.2f%% (%d/%d claims within SLA), Avg Processing: %.1f days",
                    complianceRate, claimsWithinSla, totalClaims, avgProcessingDays);
        }
    }
}

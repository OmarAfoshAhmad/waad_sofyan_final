package com.waad.tba.modules.preauthorization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for PreAuthorization Dashboard Statistics and Analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthDashboardDto {

    /**
     * Overall Statistics
     */
    private OverallStats overallStats;

    /**
     * Status Distribution
     */
    private StatusDistribution statusDistribution;

    /**
     * High Priority Items
     */
    private List<PreAuthSummaryDto> highPriorityQueue;

    /**
     * Expiring Soon Items (within 7 days)
     */
    private List<PreAuthSummaryDto> expiringSoon;

    /**
     * Trend Data (last 30 days)
     */
    private List<TrendData> trends;

    /**
     * Top Providers (by volume)
     */
    private List<ProviderSummary> topProviders;

    /**
     * Recent Activity (last 10 actions)
     */
    private List<RecentActivity> recentActivity;

    // ==================== NESTED DTOs ====================

    /**
     * Overall Statistics Summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverallStats {
        private long totalCount;
        private long pendingCount;
        private long approvedCount;
        private long rejectedCount;
        
        private BigDecimal totalRequestedAmount;
        private BigDecimal totalApprovedAmount;
        private BigDecimal averageApprovedAmount;
        
        private double approvalRate;  // Percentage
        private double rejectionRate; // Percentage
    }

    /**
     * Status-wise Distribution
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusDistribution {
        private long pending;
        private long approved;
        private long rejected;
        private long cancelled;
        private long expired;
        private long underReview;
        
        private BigDecimal pendingAmount;
        private BigDecimal approvedAmount;
        private BigDecimal rejectedAmount;
    }

    /**
     * Trend Data Point (daily aggregation)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        private LocalDate date;
        private long created;
        private long approved;
        private long rejected;
        private BigDecimal totalAmount;
        private BigDecimal approvedAmount;
    }

    /**
     * Provider Summary (top performers)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderSummary {
        private Long providerId;
        private String providerName;
        private String licenseNumber;
        private long totalPreAuths;
        private long approvedCount;
        private BigDecimal totalApprovedAmount;
        private double approvalRate;
    }

    /**
     * Recent Activity Item
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivity {
        private Long preAuthId;
        private String referenceNumber;
        private String action;        // CREATED, APPROVED, REJECTED, etc.
        private String actionBy;
        private String timestamp;
        private String notes;
    }

    /**
     * PreAuth Summary (for queues)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreAuthSummaryDto {
        private Long id;
        private String referenceNumber;
        private String memberName;
        private String providerName;
        private String serviceName;
        private BigDecimal requestedAmount;
        private String status;
        private String priority;
        private LocalDate expiryDate;
        private Integer daysUntilExpiry;
    }
}

package com.waad.tba.modules.benefitpolicy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Per-policy result of a bulk delete (soft-cancel) operation — one blocked
 * policy (active members still enrolled) must never discard the outcome of
 * the other policies in the same batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkBenefitPolicyResultDto {
    private int totalCount;
    private int successCount;
    private int failedCount;
    private List<PolicyResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyResult {
        private Long policyId;
        private String policyName;
        private boolean success;
        private String message;
    }
}

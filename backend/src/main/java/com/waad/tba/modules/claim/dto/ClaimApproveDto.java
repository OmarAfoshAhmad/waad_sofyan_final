package com.waad.tba.modules.claim.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for approving a claim.
 * 
 * Used by: POST /api/claims/{id}/approve
 * 
 * Contains reviewer decisions and notes only. All monetary values are
 * calculated and validated by the backend financial engine.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimApproveDto {

    private List<LineDecision> lineDecisions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDecision {
        private Long lineId;
        private ClaimLineReviewDecision decision;
        private String reason;
    }
    
    /**
     * Optional notes from the reviewer.
     */
    private String notes;
    
}

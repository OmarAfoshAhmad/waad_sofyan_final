package com.waad.tba.modules.employer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Per-employer result of a bulk archive/restore operation — one failure
 * (e.g. an employer with active members blocking archive) must never discard
 * the outcome of the other employers in the same batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkEmployerResultDto {
    private int totalCount;
    private int successCount;
    private int failedCount;
    private List<EmployerResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployerResult {
        private Long employerId;
        private String employerName;
        private boolean success;
        private String message;
    }
}

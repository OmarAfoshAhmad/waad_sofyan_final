package com.waad.tba.modules.providercontract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed, per-contract outcome of a bulk operation (update / activate / suspend /
 * terminate / delete). Never collapse a partially-successful batch into one generic
 * "failed"/"succeeded" message — the caller needs to know exactly which contracts
 * succeeded, which failed, and why.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkProviderContractResultDto {

    private int totalCount;
    private int successCount;
    private int failedCount;

    @Builder.Default
    private List<ContractResult> results = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContractResult {
        private Long contractId;
        private String contractCode;
        private boolean success;
        private String message;
    }

    public static BulkProviderContractResultDto empty() {
        return BulkProviderContractResultDto.builder()
                .totalCount(0)
                .successCount(0)
                .failedCount(0)
                .results(new ArrayList<>())
                .build();
    }
}

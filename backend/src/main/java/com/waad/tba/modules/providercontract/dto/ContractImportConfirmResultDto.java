package com.waad.tba.modules.providercontract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractImportConfirmResultDto {
    private int totalRows;
    private int skippedInvalidCount;
    private int successCount;
    private int failedCount;
    private List<RowResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowResult {
        private int rowNumber;
        private String providerName;
        private String contractCode;
        private boolean success;
        private String message;
    }
}

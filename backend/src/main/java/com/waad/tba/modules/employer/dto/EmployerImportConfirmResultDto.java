package com.waad.tba.modules.employer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployerImportConfirmResultDto {
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
        private String employerName;
        private String employerCode;
        private String policyCode;
        /** CREATE / UPDATE / NO_CHANGE — null for rows that failed before an action could be determined. */
        private String action;
        private boolean success;
        private String message;
    }
}

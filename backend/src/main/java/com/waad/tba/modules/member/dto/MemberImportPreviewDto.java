package com.waad.tba.modules.member.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for member import preview.
 * Shows parsed rows before confirmation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberImportPreviewDto {
    
    /**
     * Unique batch ID for this import session
     */
    private String batchId;
    
    /**
     * Original file name
     */
    private String fileName;
    
    /**
     * Total rows found in Excel (excluding header)
     */
    private int totalRows;

    /**
     * Rows that passed validation and can be imported
     */
    private int validRows;

    /**
     * Rows that failed validation and will be skipped
     */
    private int invalidRows;
    
    /**
     * Rows that will be created (new card numbers)
     */
    private int newCount;
    
    /**
     * Rows that will be updated (existing card numbers)
     */
    private int updateCount;
    
    /**
     * Rows with non-critical warnings (will be imported)
     */
    private int warningCount;
    
    /**
     * Rows with validation errors (will be skipped)
     */
    private int errorCount;
    
    /**
     * Column headers detected in Excel
     */
    private List<String> detectedColumns;
    
    /**
     * Mapped columns (Excel header → field)
     */
    private Map<String, String> columnMappings;
    
    /**
     * Preview of first N rows (parsed data)
     */
    private List<MemberImportRowDto> previewRows;
    
    /**
     * Validation errors found
     */
    private List<ImportValidationErrorDto> validationErrors;

    /**
     * Validation errors alias for API contract compatibility
     */
    private List<ImportValidationErrorDto> errors;
    
    /**
     * Is the preview ready to be confirmed?
     */
    private boolean canProceed;
    
    /**
     * Key used for matching existing members
     */
    private String matchKeyUsed;
    private List<String> warnings;
    
    /**
     * Available employers for selection (NEW)
     */
    private List<EmployerOptionDto> availableEmployers;
    
    /**
     * Available benefit policies for selection (NEW)
     */
    private List<BenefitPolicyOptionDto> availableBenefitPolicies;

    /** Distinct resolved employers of every importable row; execution rechecks it. */
    private java.util.Set<Long> resolvedEmployerIds;
    
    /**
     * Single row in preview
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberImportRowDto {
        private int rowNumber;
        private String cardNumber;
        private String fullName;
        private String employerName;
        /**
         * Row status:
         * - NEW: New member will be created
         * - UPDATE: Existing member will be updated
         * - WARNING: Row has non-critical issues but can be imported
         * - ERROR: Row has critical errors and will be skipped
         */
        private String status;  // NEW, UPDATE, WARNING, ERROR
        private Map<String, String> attributes;  // Extra columns
        private List<String> errors;    // Critical errors (block import)
        private List<String> warnings;  // Non-critical warnings (allow import)
    }
    
    /**
     * Validation error for a row
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportValidationErrorDto {
        private int rowNumber;
        private String field;
        private String value;
        private String message;
        private String severity;  // ERROR, WARNING
    }
    
    /**
     * Employer option for selection (NEW)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployerOptionDto {
        private Long id;
        private String code;
        private String name;
        private Boolean active;
    }
    
    /**
     * Benefit Policy option for selection (NEW)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenefitPolicyOptionDto {
        private Long id;
        private String policyNumber;
        private String name;
        private Long employerId;  // FK to filter policies by employer
        private Boolean isActive;
    }
}

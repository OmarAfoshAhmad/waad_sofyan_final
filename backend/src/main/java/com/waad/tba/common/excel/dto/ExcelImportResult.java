package com.waad.tba.common.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified result DTO for Excel imports across all modules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportResult {

    /**
     * Import summary statistics
     */
    @Builder.Default
    private ImportSummary summary = new ImportSummary();

    /**
     * List of errors encountered during import
     */
    @Builder.Default
    private List<ImportError> errors = new ArrayList<>();

    /**
     * Import timestamp
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Import success status
     */
    private boolean success;

    /**
     * Overall message (Arabic)
     */
    private String messageAr;

    /**
     * Overall message (English)
     */
    private String messageEn;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportSummary {
        /**
         * Total rows processed
         */
        private int totalRows;

        /**
         * Number of records created
         */
        private int created;

        /**
         * Number of records skipped (validation failed but non-critical)
         */
        private int skipped;

        /**
         * Number of records rejected (validation failed - critical)
         */
        private int rejected;

        /**
         * Number of records updated (Phase 2)
         */
        @Builder.Default
        private int updated = 0;

        /**
         * Number of records failed (exception during save)
         */
        @Builder.Default
        private int failed = 0;

        // ── Breakdown by member type ──────────────────────────────────────────
        @Builder.Default
        private int principalsCreated = 0;

        @Builder.Default
        private int dependentsCreated = 0;

        @Builder.Default
        private int principalsSkipped = 0;

        @Builder.Default
        private int dependentsSkipped = 0;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportError {
        /**
         * Row number in Excel (1-based, excluding header)
         */
        private int rowNumber;

        /**
         * Error type
         */
        private ErrorType errorType;

        /**
         * Column name where error occurred (if applicable)
         */
        private String columnName;

        /**
         * Field name in the entity (if applicable)
         */
        private String fieldName;

        /**
         * Error message (Arabic)
         */
        private String messageAr;

        /**
         * Error message (English)
         */
        private String messageEn;

        /**
         * Value that caused the error
         */
        private String value;

        /**
         * Optional identifier for the row (e.g., member name)
         */
        private String rowIdentifier;

        public enum ErrorType {
            /**
             * Required field is missing or empty
             */
            MISSING_REQUIRED,

            /**
             * Lookup failed (e.g., employer not found)
             */
            LOOKUP_FAILED,

            /**
             * Invalid data format (e.g., invalid date)
             */
            INVALID_FORMAT,

            /**
             * Invalid enum value
             */
            INVALID_ENUM,

            /**
             * Value exceeds maximum length
             */
            MAX_LENGTH_EXCEEDED,

            /**
             * Duplicate value (uniqueness constraint)
             */
            DUPLICATE,

            /**
             * System-generated field provided (should be ignored)
             */
            SYSTEM_GENERATED_IGNORED,

            /**
             * Business rule violation
             */
            BUSINESS_RULE_VIOLATION,

            /**
             * Unexpected exception during processing
             */
            PROCESSING_ERROR
        }
    }
}

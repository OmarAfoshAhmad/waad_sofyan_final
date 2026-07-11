package com.waad.tba.modules.providercontract.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a bulk price-list import operation that spans multiple providers.
 * One {@link ProviderImportResult} entry is produced for every distinct
 * provider (facility) found in the uploaded Excel file.
 */
@Data
@Builder
public class BulkImportResultDto {

    /** Total number of distinct providers (facilities) found in the file. */
    private int totalProviders;

    /** Number of providers that were newly created during this import. */
    private int providersCreated;

    /** Number of providers that already existed and were matched by name. */
    private int providersMatched;

    /** Total pricing rows processed across all providers. */
    private int totalRowsProcessed;

    /** Total pricing items created (new). */
    private int totalCreated;

    /** Total pricing items updated (price changed). */
    private int totalUpdated;

    /** Total rows skipped (empty or example rows). */
    private int totalSkipped;

    /** Total rows that failed (parse / DB error). */
    private int totalFailed;

    /** Per-provider breakdown. */
    @Builder.Default
    private List<ProviderImportResult> providerResults = new ArrayList<>();

    /** Human-readable summary in Arabic. */
    private String summaryAr;

    /** Human-readable summary in English. */
    private String summaryEn;

    // ─────────────────────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class ProviderImportResult {

        /** Arabic name of the provider as read from the Excel file. */
        private String providerName;

        /** DB id of the provider (after creation or lookup). */
        private Long providerId;

        /** DB id of the contract used (after creation or lookup). */
        private Long contractId;

        /** True when provider was created in this import run. */
        private boolean providerCreated;

        /** True when a fresh contract was created for this provider. */
        private boolean contractCreated;

        private int rowsProcessed;
        private int created;
        private int updated;
        private int skipped;
        private int failed;

        /** Row-level errors for this provider (max 50 captured). */
        @Builder.Default
        private List<String> errors = new ArrayList<>();

        /** True when an existing DRAFT/SUSPENDED contract was activated during this import. */
        private boolean contractActivated;

        /** Short status: SUCCESS / PARTIAL / FAILED. */
        private String status;
    }
}

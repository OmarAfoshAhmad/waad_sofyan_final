package com.waad.tba.modules.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for import result after confirmation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberImportResultDto {

    /**
     * True when nothing was processed because this exact file had already been
     * imported. Not an error: the request was refused on purpose, and a screen
     * that reports it as a failure sends someone hunting for a fault that does
     * not exist.
     */
    private boolean alreadyImported;

    
    /**
     * Batch ID for tracking
     */
    private String batchId;
    
    /**
     * Import status
     */
    private String status;  // COMPLETED, PARTIAL, FAILED
    
    /**
     * Statistics
     */
    private int totalProcessed;
    private int createdCount;
    private int updatedCount;
    private int skippedCount;
    private int errorCount;
    
    /**
     * Processing time in milliseconds
     */
    private long processingTimeMs;
    
    /**
     * Completion timestamp
     */
    private LocalDateTime completedAt;
    
    /**
     * Success rate percentage
     */
    private double successRate;
    
    /**
     * Error details (if any)
     */
    private List<ImportErrorDetailDto> errors;
    
    /**
     * Summary message
     */
    private String message;
    
    /**
     * Error detail
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportErrorDetailDto {
        private int rowNumber;
        private String nationalId;
        private String errorType;
        private String field;
        private String message;
    }
}

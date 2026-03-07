package com.waad.tba.services.pdf.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for PDF Report Generation
 * 
 * This DTO wraps the actual data DTO and metadata.
 * Supports ANY DTO type for maximum flexibility.
 * 
 * @since 2026-01-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfReportRequest<T> {
    
    /**
     * Report metadata (title, orientation, tracking, etc.)
     */
    private PdfReportMetadata metadata;
    
    /**
     * Main data DTO (can be any type: MemberResponseDto, ProviderDto, etc.)
     */
    private T data;
    
    /**
     * Optional: List data for table-based reports
     */
    private List<T> dataList;
    
    /**
     * Optional: Attachments to embed in PDF
     */
    private List<PdfAttachment> attachments;
    
    /**
     * PDF attachment (image, document, etc.)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PdfAttachment {
        private String fileName;
        private String fileType; // PDF, PNG, JPG
        private byte[] fileData;
        private String caption;
    }
}

package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for provider administrative documents
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderAdminDocumentResponseDto {
    
    private Long id;
    private Long providerId;
    private String type;
    private String typeLabel;
    private String fileName;
    private String fileUrl;
    private String documentNumber;
    private LocalDate expiryDate;
    private String notes;
    private LocalDateTime uploadedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.waad.tba.modules.claim.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.dto.ClaimAttachmentDto;
import com.waad.tba.modules.claim.entity.ClaimAttachment;
import com.waad.tba.modules.claim.entity.ClaimAttachmentType;
import com.waad.tba.modules.claim.service.ClaimAttachmentService;
import com.waad.tba.common.file.FileResourceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Claim Attachment Controller
 * 
 * REST endpoints for managing claim attachments
 * 
 * Endpoints:
 * - POST   /api/claims/{id}/attachments           - Upload attachment
 * - GET    /api/claims/{id}/attachments           - List all attachments
 * - GET    /api/claims/{id}/attachments/{attId}   - Download attachment
 * - DELETE /api/claims/{id}/attachments/{attId}   - Delete attachment
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
public class ClaimAttachmentController {
    
    private final ClaimAttachmentService attachmentService;
    
    /**
     * Upload an attachment to a claim
     * 
     * @param claimId Claim ID
     * @param file File to upload
     * @param attachmentType Type of attachment (INVOICE, MEDICAL_REPORT, etc.)
     * @return Uploaded attachment details
     */
    @PostMapping("/{claimId}/attachments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<ClaimAttachmentDto>> uploadAttachment(
            @PathVariable("claimId") Long claimId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("attachmentType") ClaimAttachmentType attachmentType) {
        
        log.info("Upload attachment request: claimId={}, type={}, filename={}", 
                 claimId, attachmentType, file.getOriginalFilename());
        
        try {
            ClaimAttachment attachment = attachmentService.uploadAttachment(claimId, file, attachmentType);
            ClaimAttachmentDto dto = ClaimAttachmentDto.builder()
                .id(attachment.getId())
                .fileName(attachment.getOriginalFileName())
                .fileUrl(attachment.getFileUrl())
                .fileType(attachment.getFileType())
                .createdAt(attachment.getCreatedAt())
                .build();
            return ResponseEntity.ok(ApiResponse.success("Attachment uploaded successfully", dto));
            
        } catch (RuntimeException e) {
            log.error("Failed to upload attachment for claim {}: {}", claimId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Failed to upload attachment: " + e.getMessage()));
        }
    }
    
    /**
     * Get all attachments for a claim
     * 
     * @param claimId Claim ID
     * @return List of attachments
     */
    @GetMapping("/{claimId}/attachments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ClaimAttachmentDto>> getClaimAttachments(@PathVariable("claimId") Long claimId) {
        log.info("📋 Get attachments for claim ID: {}", claimId);
        
        List<ClaimAttachment> attachments = attachmentService.getClaimAttachments(claimId);
        
        log.info("✅ Found {} attachments for claim {}", attachments.size(), claimId);
        
        // Convert to DTOs to avoid lazy loading issues
        List<ClaimAttachmentDto> dtos = attachments.stream()
            .map(att -> {
                ClaimAttachmentDto dto = ClaimAttachmentDto.builder()
                    .id(att.getId())
                    .fileName(att.getOriginalFileName() != null ? att.getOriginalFileName() : att.getFileName())
                    .fileUrl(att.getFileUrl())
                    .fileType(att.getFileType())
                    .attachmentType(att.getAttachmentType() != null ? att.getAttachmentType().name() : null)
                    .createdAt(att.getCreatedAt())
                    .build();
                
                // 🔍 DEBUG: Log each attachment ID and details
                log.debug("  → Attachment {}: {} ({})", dto.getId(), dto.getFileName(), dto.getFileType());
                
                return dto;
            })
            .toList();
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * Download a specific attachment
     * 
     * @param claimId Claim ID (for path consistency)
     * @param attachmentId Attachment ID
     * @return File content as Resource
     */
    @GetMapping("/{claimId}/attachments/{attachmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable("claimId") Long claimId,
            @PathVariable("attachmentId") Long attachmentId) {
        
        log.info("📥 Download attachment request: claimId={}, attachmentId={}", claimId, attachmentId);
        
        try {
            ClaimAttachment attachment = attachmentService.getAttachment(attachmentId);
            
            // ⚠️ CRITICAL: Verify attachment belongs to claim (security + data integrity)
            if (!attachment.getClaim().getId().equals(claimId)) {
                log.warn("❌ SECURITY: Attachment {} does NOT belong to claim {}. Actual claim: {}", 
                         attachmentId, claimId, attachment.getClaim().getId());
                return ResponseEntity.notFound().build();
            }
            
            log.info("✅ Attachment verified. FileKey: {}, FileName: {}", 
                     attachment.getFileKey(), attachment.getOriginalFileName());
            
            byte[] fileContent = attachmentService.downloadAttachment(attachmentId);
            
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            log.info("✅ Attachment downloaded successfully. Size: {} bytes", fileContent.length);
            
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                      FileResourceUtils.buildAttachmentContentDisposition(attachment.getOriginalFileName()))
                .contentLength(fileContent.length)
                .body(resource);
                
        } catch (RuntimeException e) {
            log.error("❌ Failed to download attachment {} for claim {}: {}", 
                      attachmentId, claimId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Delete an attachment
     * 
     * @param claimId Claim ID (for path consistency)
     * @param attachmentId Attachment ID
     * @return Success message
     */
    @DeleteMapping("/{claimId}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'PROVIDER_STAFF')")
    public ResponseEntity<String> deleteAttachment(
            @PathVariable("claimId") Long claimId,
            @PathVariable("attachmentId") Long attachmentId) {
        
        log.info("Delete attachment: claimId={}, attachmentId={}", claimId, attachmentId);
        
        try {
            attachmentService.deleteAttachment(attachmentId);
            return ResponseEntity.ok("Attachment deleted successfully");
            
        } catch (RuntimeException e) {
            log.error("Failed to delete attachment {}: {}", attachmentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to delete attachment: " + e.getMessage());
        }
    }
    
    /**
     * Get attachment count for a claim
     * 
     * @param claimId Claim ID
     * @return Number of attachments
     */
    @GetMapping("/{claimId}/attachments/count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getAttachmentCount(@PathVariable("claimId") Long claimId) {
        log.info("Get attachment count for claim ID: {}", claimId);
        
        long count = attachmentService.countAttachments(claimId);
        return ResponseEntity.ok(count);
    }
}


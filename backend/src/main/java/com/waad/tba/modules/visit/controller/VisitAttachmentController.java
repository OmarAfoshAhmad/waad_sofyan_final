package com.waad.tba.modules.visit.controller;

import com.waad.tba.common.file.FileResourceUtils;
import com.waad.tba.modules.visit.entity.VisitAttachment;
import com.waad.tba.modules.visit.entity.VisitAttachmentType;
import com.waad.tba.modules.visit.service.VisitAttachmentService;
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
 * Visit Attachment Controller
 * 
 * REST endpoints for managing visit attachments
 * (medical images, lab results, prescriptions, etc.)
 * 
 * Endpoints:
 * - POST   /api/visits/{id}/attachments           - Upload attachment
 * - GET    /api/visits/{id}/attachments           - List all attachments
 * - GET    /api/visits/{id}/attachments/{attId}   - Download attachment
 * - DELETE /api/visits/{id}/attachments/{attId}   - Delete attachment
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
public class VisitAttachmentController {
    
    private final VisitAttachmentService attachmentService;
    
    /**
     * Upload an attachment to a visit
     * 
     * @param visitId Visit ID
     * @param file File to upload
     * @param attachmentType Type of attachment
     * @param description Optional description
     * @return Uploaded attachment details
     */
    @PostMapping("/{visitId}/attachments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<VisitAttachment> uploadAttachment(
            @PathVariable("visitId") Long visitId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("attachmentType") VisitAttachmentType attachmentType,
            @RequestParam(value = "description", required = false) String description) {
        
        log.info("Upload attachment request: visitId={}, type={}, filename={}", 
                 visitId, attachmentType, file.getOriginalFilename());
        
        try {
            VisitAttachment attachment = attachmentService.uploadAttachment(visitId, file, attachmentType, description);
            return ResponseEntity.ok(attachment);
            
        } catch (RuntimeException e) {
            log.error("Failed to upload attachment for visit {}: {}", visitId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }
    
    /**
     * Get all attachments for a visit
     * 
     * @param visitId Visit ID
     * @return List of attachments
     */
    @GetMapping("/{visitId}/attachments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<List<VisitAttachment>> getVisitAttachments(@PathVariable("visitId") Long visitId) {
        log.info("Get attachments for visit ID: {}", visitId);
        
        List<VisitAttachment> attachments = attachmentService.getVisitAttachments(visitId);
        return ResponseEntity.ok(attachments);
    }
    
    /**
     * Download a specific attachment
     * 
     * @param visitId Visit ID (for path consistency)
     * @param attachmentId Attachment ID
     * @return File content as Resource
     */
    @GetMapping("/{visitId}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable("visitId") Long visitId,
            @PathVariable("attachmentId") Long attachmentId) {
        
        log.info("Download attachment: visitId={}, attachmentId={}", visitId, attachmentId);
        
        try {
            VisitAttachment attachment = attachmentService.getAttachment(attachmentId);
            byte[] fileContent = attachmentService.downloadAttachment(attachmentId);
            
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                      FileResourceUtils.buildAttachmentContentDisposition(attachment.getOriginalFileName()))
                .contentLength(fileContent.length)
                .body(resource);
                
        } catch (RuntimeException e) {
            log.error("Failed to download attachment {}: {}", attachmentId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Delete an attachment
     * 
     * @param visitId Visit ID (for path consistency)
     * @param attachmentId Attachment ID
     * @return Success message
     */
    @DeleteMapping("/{visitId}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<String> deleteAttachment(
            @PathVariable("visitId") Long visitId,
            @PathVariable("attachmentId") Long attachmentId) {
        
        log.info("Delete attachment: visitId={}, attachmentId={}", visitId, attachmentId);
        
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
     * Get attachment count for a visit
     * 
     * @param visitId Visit ID
     * @return Number of attachments
     */
    @GetMapping("/{visitId}/attachments/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<Long> getAttachmentCount(@PathVariable("visitId") Long visitId) {
        log.info("Get attachment count for visit ID: {}", visitId);
        
        long count = attachmentService.countAttachments(visitId);
        return ResponseEntity.ok(count);
    }
}


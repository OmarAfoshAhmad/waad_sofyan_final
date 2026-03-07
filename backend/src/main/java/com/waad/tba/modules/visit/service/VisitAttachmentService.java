package com.waad.tba.modules.visit.service;

import com.waad.tba.common.file.FileStorageService;
import com.waad.tba.common.file.FileUploadResult;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitAttachment;
import com.waad.tba.modules.visit.entity.VisitAttachmentType;
import com.waad.tba.modules.visit.repository.VisitAttachmentRepository;
import com.waad.tba.modules.visit.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * Visit Attachment Service
 * 
 * Handles file upload, download, and management for visit attachments
 * (medical images, lab results, prescriptions, etc.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitAttachmentService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final VisitAttachmentRepository attachmentRepository;
    private final VisitRepository visitRepository;
    private final FileStorageService fileStorageService;

    /**
     * Upload an attachment for a visit
     * 
     * @param visitId        Visit ID
     * @param file           File to upload
     * @param attachmentType Type of attachment
     * @param description    Optional description
     * @return Created VisitAttachment entity
     */
    @Transactional
    public VisitAttachment uploadAttachment(Long visitId, MultipartFile file,
            VisitAttachmentType attachmentType, String description) {
        log.info("Uploading attachment for visit ID: {}, type: {}", visitId, attachmentType);

        // Validate file
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of 10 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("File type not allowed: " + contentType +
                    ". Allowed types: PDF, JPEG, PNG, DOC, DOCX");
        }

        // Verify visit exists
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new RuntimeException("Visit not found with ID: " + visitId));

        // Upload file to storage
        String folder = "visits/" + visitId;
        FileUploadResult uploadResult = fileStorageService.upload(file, folder);

        // Create attachment record
        VisitAttachment attachment = VisitAttachment.builder()
                .visit(visit)
                .fileName(uploadResult.getFileName())
                .originalFileName(uploadResult.getFileName())
                .fileKey(uploadResult.getFileKey())
                .fileType(uploadResult.getContentType())
                .fileSize(uploadResult.getSize())
                .attachmentType(attachmentType)
                .description(description)
                .uploadedBy(getCurrentUsername())
                .build();

        VisitAttachment saved = attachmentRepository.save(attachment);
        log.info("Attachment uploaded successfully: ID={}, fileKey={}", saved.getId(), saved.getFileKey());

        return saved;
    }

    /**
     * Download an attachment
     * 
     * @param attachmentId Attachment ID
     * @return File content as byte array
     */
    public byte[] downloadAttachment(Long attachmentId) {
        log.info("Downloading attachment ID: {}", attachmentId);

        VisitAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found with ID: " + attachmentId));

        return fileStorageService.download(attachment.getFileKey());
    }

    /**
     * Delete an attachment
     * 
     * @param attachmentId Attachment ID
     */
    @Transactional
    public void deleteAttachment(Long attachmentId) {
        log.info("Deleting attachment ID: {}", attachmentId);

        VisitAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found with ID: " + attachmentId));

        // Delete from storage
        fileStorageService.delete(attachment.getFileKey());

        // Delete from database
        attachmentRepository.delete(attachment);

        log.info("Attachment deleted successfully: ID={}", attachmentId);
    }

    /**
     * Get all attachments for a visit
     * 
     * @param visitId Visit ID
     * @return List of attachments
     */
    public List<VisitAttachment> getVisitAttachments(Long visitId) {
        log.info("Fetching attachments for visit ID: {}", visitId);
        return attachmentRepository.findByVisitId(visitId);
    }

    /**
     * Get a specific attachment by ID
     * 
     * @param attachmentId Attachment ID
     * @return VisitAttachment entity
     */
    public VisitAttachment getAttachment(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found with ID: " + attachmentId));
    }

    /**
     * Count attachments for a visit
     * 
     * @param visitId Visit ID
     * @return Number of attachments
     */
    public long countAttachments(Long visitId) {
        return attachmentRepository.countByVisitId(visitId);
    }

    /**
     * Delete all attachments for a visit
     * 
     * @param visitId Visit ID
     */
    @Transactional
    public void deleteAllVisitAttachments(Long visitId) {
        log.info("Deleting all attachments for visit ID: {}", visitId);

        List<VisitAttachment> attachments = attachmentRepository.findByVisitId(visitId);

        // Delete files from storage
        for (VisitAttachment attachment : attachments) {
            try {
                fileStorageService.delete(attachment.getFileKey());
            } catch (Exception e) {
                log.error("Failed to delete file: {}", attachment.getFileKey(), e);
            }
        }

        // Delete from database
        attachmentRepository.deleteByVisitId(visitId);

        log.info("All attachments deleted for visit ID: {}", visitId);
    }

    /**
     * Get current authenticated username
     * 
     * @return Username or "system"
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("Could not get current username", e);
        }
        return "system";
    }
}

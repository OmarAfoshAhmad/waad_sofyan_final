package com.waad.tba.modules.claim.service;

import com.waad.tba.common.file.FileStorageService;
import com.waad.tba.common.file.FileUploadResult;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimAttachment;
import com.waad.tba.modules.claim.entity.ClaimAttachmentType;
import com.waad.tba.modules.claim.repository.ClaimAttachmentRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
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
 * Claim Attachment Service
 * 
 * Handles file upload, download, and management for claim attachments
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimAttachmentService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ClaimAttachmentRepository attachmentRepository;
    private final ClaimRepository claimRepository;
    private final FileStorageService fileStorageService;

    /**
     * Upload an attachment for a claim
     * 
     * @param claimId        Claim ID
     * @param file           File to upload
     * @param attachmentType Type of attachment
     * @return Created ClaimAttachment entity
     */
    @Transactional
    public ClaimAttachment uploadAttachment(Long claimId, MultipartFile file, ClaimAttachmentType attachmentType) {
        log.info("Uploading attachment for claim ID: {}, type: {}", claimId, attachmentType);

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

        // Verify claim exists
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found with ID: " + claimId));

        // Upload file to storage
        String folder = "claims/" + claimId;
        FileUploadResult uploadResult = fileStorageService.upload(file, folder);

        // Create attachment record
        ClaimAttachment attachment = ClaimAttachment.builder()
                .claim(claim)
                .fileName(uploadResult.getFileName())
                .fileUrl(uploadResult.getUrl())
                .filePath(uploadResult.getUrl())
                .fileType(uploadResult.getContentType())
                .fileKey(uploadResult.getFileKey())
                .originalFileName(uploadResult.getFileName())
                .fileSize(uploadResult.getSize())
                .uploadedBy(getCurrentUsername())
                .attachmentType(attachmentType)
                .build();

        ClaimAttachment saved = attachmentRepository.save(attachment);
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

        ClaimAttachment attachment = attachmentRepository.findById(attachmentId)
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

        ClaimAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found with ID: " + attachmentId));

        // Delete from storage
        fileStorageService.delete(attachment.getFileKey());

        // Delete from database
        attachmentRepository.delete(attachment);

        log.info("Attachment deleted successfully: ID={}", attachmentId);
    }

    /**
     * Get all attachments for a claim
     * 
     * @param claimId Claim ID
     * @return List of attachments
     */
    public List<ClaimAttachment> getClaimAttachments(Long claimId) {
        log.info("Fetching attachments for claim ID: {}", claimId);
        return attachmentRepository.findByClaimId(claimId);
    }

    /**
     * Get a specific attachment by ID
     * 
     * @param attachmentId Attachment ID
     * @return ClaimAttachment entity
     */
    public ClaimAttachment getAttachment(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found with ID: " + attachmentId));
    }

    /**
     * Count attachments for a claim
     * 
     * @param claimId Claim ID
     * @return Number of attachments
     */
    public long countAttachments(Long claimId) {
        return attachmentRepository.countByClaimId(claimId);
    }

    /**
     * Delete all attachments for a claim
     * 
     * @param claimId Claim ID
     */
    @Transactional
    public void deleteAllClaimAttachments(Long claimId) {
        log.info("Deleting all attachments for claim ID: {}", claimId);

        List<ClaimAttachment> attachments = attachmentRepository.findByClaimId(claimId);

        // Delete files from storage
        for (ClaimAttachment attachment : attachments) {
            try {
                fileStorageService.delete(attachment.getFileKey());
            } catch (Exception e) {
                log.error("Failed to delete file: {}", attachment.getFileKey(), e);
            }
        }

        // Delete from database
        attachmentRepository.deleteByClaimId(claimId);

        log.info("All attachments deleted for claim ID: {}", claimId);
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

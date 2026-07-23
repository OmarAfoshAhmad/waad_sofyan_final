package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.common.file.FileUploadResult;
import com.waad.tba.common.file.LocalFileStorageService;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationAttachment;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationAttachmentRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing PreAuthorization Attachments
 * Handles file upload, download, and deletion
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreAuthorizationAttachmentService {

    private final PreAuthorizationAttachmentRepository attachmentRepository;
    private final PreAuthorizationRepository preAuthorizationRepository;
    private final LocalFileStorageService fileStorageService;
    private final AuthorizationService authorizationService;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_TYPES = List.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/gif",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    /**
     * Upload attachment to a pre-authorization
     */
    @Transactional
    public PreAuthorizationAttachment uploadAttachment(Long preAuthorizationId, MultipartFile file, String attachmentType, String uploadedBy) {
        assertCanAccessPreAuthorization(preAuthorizationId);

        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed (10MB)");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("File type not allowed: " + contentType);
        }

        try {
            // Use centralized file storage service
            FileUploadResult uploadResult = fileStorageService.upload(file, "pre-authorizations/" + preAuthorizationId);

            // Create attachment record
            PreAuthorizationAttachment attachment = PreAuthorizationAttachment.builder()
                    .preAuthorizationId(preAuthorizationId)
                    .originalFileName(file.getOriginalFilename())
                    // storedFileName is @Transient - no DB column
                    .filePath(uploadResult.getFilePath()) // Absolute path on disk
                    .fileType(contentType)
                    .fileSize(file.getSize())
                    .attachmentType(attachmentType)
                    .createdBy(uploadedBy)
                    .build();

            PreAuthorizationAttachment saved = attachmentRepository.save(attachment);
            log.info("✅ Uploaded attachment {} for pre-authorization {} by {}", saved.getId(), preAuthorizationId, uploadedBy);
            
            return saved;

        } catch (Exception e) {
            log.error("Failed to upload attachment: {}", e.getMessage());
            throw new RuntimeException("Failed to save file: " + e.getMessage());
        }
    }

    /**
     * Get all attachments for a pre-authorization
     */
    public List<PreAuthorizationAttachment> getAttachments(Long preAuthorizationId) {
        assertCanAccessPreAuthorization(preAuthorizationId);
        return attachmentRepository.findByPreAuthorizationId(preAuthorizationId);
    }

    /**
     * Get single attachment by ID
     */
    public PreAuthorizationAttachment getAttachment(Long preAuthorizationId, Long attachmentId) {
        assertCanAccessPreAuthorization(preAuthorizationId);
        PreAuthorizationAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));
        if (!preAuthorizationId.equals(attachment.getPreAuthorizationId())) {
            throw new IllegalArgumentException("Attachment not found: " + attachmentId);
        }
        return attachment;
    }

    /**
     * Download attachment content
     */
    public byte[] downloadAttachment(Long preAuthorizationId, Long attachmentId) {
        PreAuthorizationAttachment attachment = getAttachment(preAuthorizationId, attachmentId);
        
        try {
            // Extract fileKey from filePath for centralized download
            String filePath = attachment.getFilePath();
            String fileKey = filePath;
            if (filePath.contains("/uploads/")) {
                fileKey = filePath.substring(filePath.indexOf("/uploads/") + 9);
            }
            
            return fileStorageService.download(fileKey);
        } catch (Exception e) {
            log.error("Failed to read attachment {}: {}", attachmentId, e.getMessage());
            throw new RuntimeException("Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Delete attachment
     */
    @Transactional
    public void deleteAttachment(Long preAuthorizationId, Long attachmentId) {
        PreAuthorizationAttachment attachment = getAttachment(preAuthorizationId, attachmentId);
        
        try {
            // Extract fileKey from filePath
            String filePath = attachment.getFilePath();
            if (filePath != null) {
                String fileKey = filePath;
                if (filePath.contains("/uploads/")) {
                    fileKey = filePath.substring(filePath.indexOf("/uploads/") + 9);
                }
                fileStorageService.delete(fileKey);
            }
            
            // Delete record
            attachmentRepository.delete(attachment);
            log.info("✅ Deleted attachment {} from pre-authorization {}", 
                    attachmentId, attachment.getPreAuthorizationId());
            
        } catch (Exception e) {
            log.error("Failed to delete attachment: {}", e.getMessage());
            // Still delete the record even if file deletion fails
            attachmentRepository.delete(attachment);
        }
    }

    /**
     * Count attachments for a pre-authorization
     */
    public long countAttachments(Long preAuthorizationId) {
        assertCanAccessPreAuthorization(preAuthorizationId);
        return attachmentRepository.countByPreAuthorizationId(preAuthorizationId);
    }

    private void assertCanAccessPreAuthorization(Long preAuthorizationId) {
        var currentUser = authorizationService.requireCurrentUser();
        PreAuthorization preAuthorization = preAuthorizationRepository.findById(preAuthorizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pre-authorization not found: " + preAuthorizationId));

        boolean allowed = authorizationService.isInternalStaff(currentUser)
                || (authorizationService.isProvider(currentUser)
                    && currentUser.getProviderId() != null
                    && currentUser.getProviderId().equals(preAuthorization.getProviderId()))
                || (authorizationService.isEmployerAdmin(currentUser)
                    && authorizationService.canAccessMember(currentUser, preAuthorization.getMemberId()));

        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access to pre-authorization attachment denied");
        }
    }
}

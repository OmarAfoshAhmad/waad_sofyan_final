package com.waad.tba.common.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Local File Storage Service Implementation
 * 
 * Stores files in local filesystem under /uploads directory
 * 
 * Features:
 * - File type validation (PDF, JPEG, PNG, DICOM)
 * - File size limits (10MB for documents, 50MB for images)
 * - Unique file naming with UUID
 * - Folder organization
 * 
 * @author TBA System
 * @version 1.0
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    @Value("${file.storage.local.base-path:./uploads}")
    private String basePath;

    @Value("${file.storage.max-size.document:10485760}") // 10MB
    private long maxDocumentSize;

    @Value("${file.storage.max-size.image:52428800}") // 50MB
    private long maxImageSize;

    private Path uploadPath;

    // Allowed MIME types
    private static final List<String> ALLOWED_DOCUMENT_TYPES = Arrays.asList(
            "application/pdf");

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/jpg");

    private static final List<String> ALLOWED_MEDICAL_TYPES = Arrays.asList(
            "application/dicom",
            "application/x-dicom");

    @PostConstruct
    public void init() {
        try {
            uploadPath = Paths.get(basePath).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);
            log.info("File storage initialized at: {}", uploadPath);
        } catch (IOException e) {
            throw new FileStorageException("Could not create upload directory", e);
        }
    }

    @Override
    public FileUploadResult upload(MultipartFile file, String folder) {
        // Validate file
        validateFile(file);

        // Sanitize folder and filename to prevent path traversal
        String sanitizedFolder = StringUtils.cleanPath(folder).replace("..", "").replace("/", "");
        String sanitizedFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()))
                .replace("..", "").replace("/", "");

        String uniqueFilename = UUID.randomUUID().toString() + "_" + sanitizedFilename;
        String fileKey = sanitizedFolder + "/" + uniqueFilename;

        try {
            // Security check: ensure fileKey doesn't contain traversal characters
            if (fileKey.contains("..") || fileKey.startsWith("/")) {
                throw new FileStorageException("Invalid file key: " + fileKey);
            }

            // Create folder if not exists
            Path folderPath = uploadPath.resolve(sanitizedFolder).normalize();
            if (!folderPath.startsWith(uploadPath)) {
                throw new FileStorageException("Invalid folder path: " + folder);
            }
            Files.createDirectories(folderPath);

            // Copy file to target location
            Path targetPath = uploadPath.resolve(fileKey).normalize();
            if (!targetPath.startsWith(uploadPath)) {
                throw new FileStorageException("Invalid target path: " + fileKey);
            }
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("File uploaded successfully: {}", fileKey);

            // Build result
            return FileUploadResult.builder()
                    .fileKey(fileKey)
                    .fileName(sanitizedFilename)
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .folder(folder)
                    .filePath(targetPath.toString())
                    .url("/api/files/" + fileKey + "/download")
                    .uploadedAt(LocalDateTime.now())
                    .uploadedBy(getCurrentUserId())
                    .build();

        } catch (IOException e) {
            throw new FileStorageException("Failed to store file: " + sanitizedFilename, e);
        }
    }

    @Override
    public byte[] download(String fileKey) {
        try {
            Path filePath = uploadPath.resolve(fileKey).normalize();

            // Security check: prevent directory traversal
            if (!filePath.startsWith(uploadPath)) {
                throw new FileStorageException("Invalid file path");
            }

            if (!Files.exists(filePath)) {
                throw new FileStorageException("File not found: " + fileKey);
            }

            return Files.readAllBytes(filePath);

        } catch (IOException e) {
            throw new FileStorageException("Failed to download file: " + fileKey, e);
        }
    }

    @Override
    public void delete(String fileKey) {
        try {
            Path filePath = uploadPath.resolve(fileKey).normalize();

            // Security check
            if (!filePath.startsWith(uploadPath)) {
                throw new FileStorageException("Invalid file path");
            }

            Files.deleteIfExists(filePath);
            log.info("File deleted: {}", fileKey);

        } catch (IOException e) {
            throw new FileStorageException("Failed to delete file: " + fileKey, e);
        }
    }

    @Override
    public String getPresignedUrl(String fileKey, int expiryMinutes) {
        // For local storage, return direct download URL
        // In production with S3/MinIO, generate actual presigned URL
        return "/api/files/" + fileKey + "/download";
    }

    @Override
    public boolean exists(String fileKey) {
        Path filePath = uploadPath.resolve(fileKey).normalize();
        return Files.exists(filePath);
    }

    // ===== Helper Methods =====

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new FileStorageException("Cannot upload empty file");
        }

        String contentType = file.getContentType();
        long fileSize = file.getSize();

        // Validate content type
        if (!isAllowedType(contentType)) {
            throw new FileStorageException("File type not allowed: " + contentType);
        }

        // Validate file size
        if (isImageType(contentType) && fileSize > maxImageSize) {
            throw new FileStorageException("Image file size exceeds limit: " + maxImageSize + " bytes");
        }

        if (isDocumentType(contentType) && fileSize > maxDocumentSize) {
            throw new FileStorageException("Document file size exceeds limit: " + maxDocumentSize + " bytes");
        }
    }

    private boolean isAllowedType(String contentType) {
        return ALLOWED_DOCUMENT_TYPES.contains(contentType) ||
                ALLOWED_IMAGE_TYPES.contains(contentType) ||
                ALLOWED_MEDICAL_TYPES.contains(contentType);
    }

    private boolean isImageType(String contentType) {
        return ALLOWED_IMAGE_TYPES.contains(contentType);
    }

    private boolean isDocumentType(String contentType) {
        return ALLOWED_DOCUMENT_TYPES.contains(contentType);
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : "";
    }

    private Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                // Extract user ID from authentication
                // This depends on your UserDetails implementation
                return 1L; // Placeholder
            }
        } catch (Exception e) {
            log.warn("Could not get current user ID", e);
        }
        return null;
    }
}

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

    // Medical imaging is legitimately large, but "large" is not "unbounded":
    // this type was allow-listed with no ceiling of any kind.
    @Value("${file.storage.max-size.medical:209715200}") // 200MB
    private long maxMedicalSize;

    private Path uploadPath;

    // Allowed MIME types
    private static final List<String> ALLOWED_DOCUMENT_TYPES = Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/jpg");

    private static final List<String> ALLOWED_MEDICAL_TYPES = Arrays.asList(
            "application/dicom",
            "application/x-dicom");

    /**
     * Extensions refused whatever Content-Type the client declares.
     *
     * The declared type is the uploader's word for it, and nothing more: a
     * page of script sent as image/png passes a MIME allowlist unchanged. The
     * extension is what a web server, a proxy, or a colleague's desktop will
     * later use to decide what this file *is*, so it has to agree.
     */
    private static final List<String> FORBIDDEN_EXTENSIONS = Arrays.asList(
            "html", "htm", "xhtml", "shtml", "svg", "xml",
            "js", "mjs", "jsp", "jspx", "php", "phtml", "asp", "aspx", "cgi", "pl", "py", "rb",
            "exe", "dll", "so", "bat", "cmd", "com", "sh", "ps1", "msi", "jar", "class",
            "htaccess");

    /** Leading bytes each accepted type must actually begin with. */
    private static final java.util.Map<String, byte[][]> MAGIC_BYTES = java.util.Map.of(
            "application/pdf", new byte[][] { { 0x25, 0x50, 0x44, 0x46 } }, // %PDF
            "image/jpeg", new byte[][] { { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF } },
            "image/jpg", new byte[][] { { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF } },
            "image/png", new byte[][] { { (byte) 0x89, 0x50, 0x4E, 0x47 } });

    /** Bytes 128-131 of a DICOM file; anything shorter cannot be one. */
    private static final byte[] DICOM_MAGIC = { 0x44, 0x49, 0x43, 0x4D };
    private static final int DICOM_MAGIC_OFFSET = 128;

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
        validateFile(file);
        try {
            return upload(file.getInputStream(), file.getOriginalFilename(), file.getContentType(), file.getSize(),
                    folder);
        } catch (IOException e) {
            throw new FileStorageException("Failed to read multipart file", e);
        }
    }

    @Override
    public FileUploadResult upload(java.io.InputStream inputStream, String originalFilename, String contentType,
            long size, String folder) {
        // Sanitize folder and filename to prevent path traversal
        String sanitizedFolder = StringUtils.cleanPath(folder).replace("..", "").replace("/", "");
        String sanitizedFilename = StringUtils.cleanPath(Objects.requireNonNull(originalFilename))
                .replace("..", "").replace("/", "");

        String uniqueFilename = UUID.randomUUID().toString() + "_" + sanitizedFilename;
        String fileKey = sanitizedFolder + "/" + uniqueFilename;

        try {
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
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("File saved successfully: {}", fileKey);

            return FileUploadResult.builder()
                    .fileKey(fileKey)
                    .fileName(sanitizedFilename)
                    .contentType(contentType)
                    .size(size)
                    .folder(folder)
                    .filePath(targetPath.toString())
                    // Raw file-key URLs are intentionally not exposed. Resource-specific
                    // controllers build authorized claim/visit/pre-auth/member URLs.
                    .url(null)
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
        throw new UnsupportedOperationException(
                "Raw file-key URLs are disabled; use a resource-specific authorized endpoint");
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

        // Medical imaging was allow-listed without ever being size-checked:
        // neither isImageType nor isDocumentType covers it, so a DICOM upload
        // had no ceiling at all. Imaging is legitimately large, so it gets its
        // own limit rather than being folded into the image one.
        if (isMedicalType(contentType) && fileSize > maxMedicalSize) {
            throw new FileStorageException("Medical file size exceeds limit: " + maxMedicalSize + " bytes");
        }

        rejectForbiddenExtension(file.getOriginalFilename());
        verifyContentMatchesDeclaredType(file, contentType);
    }

    /**
     * The extension has to be consistent with the content type the caller
     * claims, because the two are read by different things at different times
     * and only one of them is checked at upload.
     */
    private void rejectForbiddenExtension(String originalFilename) {
        if (originalFilename == null) {
            return;
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return;
        }
        String extension = originalFilename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        if (FORBIDDEN_EXTENSIONS.contains(extension)) {
            throw new FileStorageException("File extension not allowed: ." + extension);
        }
    }

    /**
     * Checks the bytes rather than the claim. Content-Type on a multipart part
     * is supplied by whoever is uploading, so an allowlist built on it alone
     * accepts anything as long as the label is right.
     *
     * Types with no stable signature (Word's OOXML container, DICOM variants
     * that omit the preamble) are passed through: refusing what cannot be
     * verified would reject legitimate clinical files, and the extension check
     * plus the storage rules still apply to them.
     */
    private void verifyContentMatchesDeclaredType(MultipartFile file, String contentType) {
        byte[][] signatures = MAGIC_BYTES.get(contentType);
        if (signatures == null && !isMedicalType(contentType)) {
            return;
        }
        try {
            byte[] head = readHead(file, isMedicalType(contentType)
                    ? DICOM_MAGIC_OFFSET + DICOM_MAGIC.length
                    : 8);
            if (isMedicalType(contentType)) {
                // A DICOM file without the standard preamble is still valid;
                // only contradict the claim when the preamble is present and
                // says something else.
                if (head.length >= DICOM_MAGIC_OFFSET + DICOM_MAGIC.length
                        && !startsWithAt(head, DICOM_MAGIC, DICOM_MAGIC_OFFSET)) {
                    throw new FileStorageException("File content does not match declared type: " + contentType);
                }
                return;
            }
            for (byte[] signature : signatures) {
                if (startsWithAt(head, signature, 0)) {
                    return;
                }
            }
            throw new FileStorageException("File content does not match declared type: " + contentType);
        } catch (IOException e) {
            throw new FileStorageException("Could not read uploaded file for verification", e);
        }
    }

    private byte[] readHead(MultipartFile file, int length) throws IOException {
        try (java.io.InputStream in = file.getInputStream()) {
            return in.readNBytes(length);
        }
    }

    private static boolean startsWithAt(byte[] data, byte[] signature, int offset) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isMedicalType(String contentType) {
        return ALLOWED_MEDICAL_TYPES.contains(contentType);
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
                Object principal = authentication.getPrincipal();
                if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                    // Extract user ID from username via the authentication name
                    // The actual user ID resolution happens at the service layer;
                    // here we store the username as a traceable identifier
                    log.debug("File upload by authenticated user: {}", userDetails.getUsername());
                    return null; // Username is stored separately via uploadedBy field
                }
            }
        } catch (Exception e) {
            log.warn("Could not get current user ID", e);
        }
        return null;
    }
}

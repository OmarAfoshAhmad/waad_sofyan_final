package com.waad.tba.common.file;

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

/**
 * File Controller
 * 
 * REST endpoints for file upload, download, and management
 * 
 * Endpoints:
 * - POST   /api/files/upload         - Upload file
 * - GET    /api/files/{key}/download - Download file
 * - DELETE /api/files/{key}          - Delete file
 * - GET    /api/files/{key}/url      - Get presigned URL
 * 
 * @author TBA System
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor@SuppressWarnings("null")public class FileController {
    
    private final FileStorageService fileStorageService;
    
    /**
     * Upload a file
     * 
     * @param file MultipartFile to upload
     * @param folder Target folder (claims, preauth, members, etc.)
     * @param description Optional file description
     * @return FileUploadResult with file details
     */
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FileUploadResult> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder,
            @RequestParam(value = "description", required = false) String description) {
        
        log.info("Uploading file: {} to folder: {}", file.getOriginalFilename(), folder);
        
        try {
            FileUploadResult result = fileStorageService.upload(file, folder);
            return ResponseEntity.ok(result);
            
        } catch (FileStorageException e) {
            log.error("File upload failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }
    
    /**
     * Download a file
     * 
     * @param fileKey File identifier (folder/filename)
     * @return File content with appropriate headers
     */
    @GetMapping("/{folder}/{filename}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable("folder") String folder,
            @PathVariable("filename") String filename) {
        
        String fileKey = folder + "/" + filename;
        log.info("Downloading file: {}", fileKey);
        
        try {
            byte[] fileContent = fileStorageService.download(fileKey);
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, FileResourceUtils.buildAttachmentContentDisposition(filename))
                .body(resource);
                
        } catch (FileStorageException e) {
            log.error("File download failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Preview a file (inline display - no download)
     * 
     * Medical-grade document preview for claims/pre-auth attachments.
     * Returns file with Content-Disposition: inline for browser preview.
     * 
     * @param folder Folder name
     * @param filename File name
     * @return File content with inline display headers
     */
    @GetMapping("/{folder}/{filename}/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> previewFile(
            @PathVariable("folder") String folder,
            @PathVariable("filename") String filename) {
        
        String fileKey = folder + "/" + filename;
        log.info("Previewing file: {}", fileKey);
        
        try {
            byte[] fileContent = fileStorageService.download(fileKey);
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            // Determine content type from file extension
            MediaType contentType = determineMediaType(filename);
            
            return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, FileResourceUtils.buildInlineContentDisposition(filename))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600") // Cache for 1 hour
                .body(resource);
                
        } catch (FileStorageException e) {
            log.error("File preview failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Determine MediaType from file extension
     */
    private MediaType determineMediaType(String filename) {
        String lowerFilename = filename.toLowerCase();
        
        if (lowerFilename.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        } else if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (lowerFilename.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (lowerFilename.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        } else if (lowerFilename.endsWith(".bmp")) {
            return MediaType.parseMediaType("image/bmp");
        } else if (lowerFilename.endsWith(".tiff") || lowerFilename.endsWith(".tif")) {
            return MediaType.parseMediaType("image/tiff");
        } else if (lowerFilename.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        } else if (lowerFilename.endsWith(".doc") || lowerFilename.endsWith(".docx")) {
            return MediaType.parseMediaType("application/msword");
        } else if (lowerFilename.endsWith(".xls") || lowerFilename.endsWith(".xlsx")) {
            return MediaType.parseMediaType("application/vnd.ms-excel");
        } else {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
    
    /**
     * Delete a file
     * 
     * @param folder Folder name
     * @param filename File name
     * @return Success/failure response
     */
    @DeleteMapping("/{folder}/{filename}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> deleteFile(
            @PathVariable("folder") String folder,
            @PathVariable("filename") String filename) {
        
        String fileKey = folder + "/" + filename;
        log.info("Deleting file: {}", fileKey);
        
        try {
            fileStorageService.delete(fileKey);
            return ResponseEntity.ok("File deleted successfully");
            
        } catch (FileStorageException e) {
            log.error("File deletion failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to delete file: " + e.getMessage());
        }
    }
    
    /**
     * Get presigned URL for file access
     * 
     * @param folder Folder name
     * @param filename File name
     * @param expiryMinutes URL validity duration (default: 60 minutes)
     * @return Presigned URL
     */
    @GetMapping("/{folder}/{filename}/url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> getPresignedUrl(
            @PathVariable("folder") String folder,
            @PathVariable("filename") String filename,
            @RequestParam(value = "expiryMinutes", defaultValue = "60") int expiryMinutes) {
        
        String fileKey = folder + "/" + filename;
        log.info("Generating presigned URL for: {}", fileKey);
        
        try {
            String url = fileStorageService.getPresignedUrl(fileKey, expiryMinutes);
            return ResponseEntity.ok(url);
            
        } catch (FileStorageException e) {
            log.error("URL generation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    /**
     * Check if file exists
     * 
     * @param folder Folder name
     * @param filename File name
     * @return true if exists, false otherwise
     */
    @GetMapping("/{folder}/{filename}/exists")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> fileExists(
            @PathVariable("folder") String folder,
            @PathVariable("filename") String filename) {
        
        String fileKey = folder + "/" + filename;
        boolean exists = fileStorageService.exists(fileKey);
        return ResponseEntity.ok(exists);
    }
}


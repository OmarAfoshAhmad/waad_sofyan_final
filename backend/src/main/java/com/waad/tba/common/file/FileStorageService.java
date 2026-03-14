package com.waad.tba.common.file;

import org.springframework.web.multipart.MultipartFile;

/**
 * File Storage Service Interface
 * 
 * Provides abstraction for file storage operations.
 * Supports multiple implementations: Local, S3, MinIO, etc.
 * 
 * @author TBA System
 * @version 1.0
 */
public interface FileStorageService {
    
    /**
     * Upload a file to storage
     * 
     * @param file MultipartFile to upload
     * @param folder Target folder path (e.g., "claims", "preauth", "members")
     * @return FileUploadResult with file key, URL, size, etc.
     * @throws FileStorageException if upload fails
     */
    FileUploadResult upload(MultipartFile file, String folder);
    
    /**
     * Upload a file from an input stream
     */
    FileUploadResult upload(java.io.InputStream inputStream, String originalFilename, String contentType, long size, String folder);
    
    /**
     * Download a file from storage
     * 
     * @param fileKey Unique file identifier
     * @return File content as byte array
     * @throws FileStorageException if file not found or download fails
     */
    byte[] download(String fileKey);
    
    /**
     * Delete a file from storage
     * 
     * @param fileKey Unique file identifier
     * @throws FileStorageException if deletion fails
     */
    void delete(String fileKey);
    
    /**
     * Generate a presigned URL for temporary access
     * 
     * @param fileKey Unique file identifier
     * @param expiryMinutes URL validity duration in minutes
     * @return Presigned URL string
     * @throws FileStorageException if URL generation fails
     */
    String getPresignedUrl(String fileKey, int expiryMinutes);
    
    /**
     * Check if a file exists in storage
     * 
     * @param fileKey Unique file identifier
     * @return true if file exists, false otherwise
     */
    boolean exists(String fileKey);
}

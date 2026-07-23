package com.waad.tba.modules.provider.service;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.file.FileUploadResult;
import com.waad.tba.common.file.LocalFileStorageService;
import com.waad.tba.modules.provider.dto.ProviderAdminDocumentCreateDto;
import com.waad.tba.modules.provider.dto.ProviderAdminDocumentResponseDto;
import com.waad.tba.modules.provider.entity.ProviderAdminDocument;
import com.waad.tba.modules.provider.repository.ProviderAdminDocumentRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing provider administrative documents
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderAdminDocumentService {
    
    private final ProviderAdminDocumentRepository documentRepository;
    private final ProviderRepository providerRepository;
    private final LocalFileStorageService fileStorageService;
    private final AuthorizationService authorizationService;
    
    /**
     * Get all documents for a provider
     */
    @Transactional(readOnly = true)
    public List<ProviderAdminDocumentResponseDto> getDocumentsByProviderId(Long providerId) {
        log.debug("Getting documents for provider: {}", providerId);
        assertCanAccessProvider(providerId);
        
        // Validate provider exists
        if (!providerRepository.existsById(providerId)) {
            throw new ResourceNotFoundException("Provider", "id", providerId);
        }
        
        return documentRepository.findByProviderId(providerId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Create new provider document
     */
    @Transactional
    public ProviderAdminDocumentResponseDto createDocument(
            Long providerId, 
            ProviderAdminDocumentCreateDto dto, 
            MultipartFile file) {
        
        log.info("Creating document for provider {} of type {}", providerId, dto.getType());
        assertCanAccessProvider(providerId);
        
        // Validate provider exists
        if (!providerRepository.existsById(providerId)) {
            throw new ResourceNotFoundException("Provider", "id", providerId);
        }
        
        // Upload file if provided
        String fileUrl = null;
        String filePath = null;
        if (file != null && !file.isEmpty()) {
            try {
                FileUploadResult uploadResult = fileStorageService.upload(file, "provider-documents");
                fileUrl = uploadResult.getUrl();
                filePath = uploadResult.getFilePath();
                log.debug("File uploaded successfully: {}", fileUrl);
            } catch (Exception e) {
                log.error("Failed to upload file", e);
                throw new RuntimeException("Failed to upload file: " + e.getMessage());
            }
        }
        
        // Create document entity
        ProviderAdminDocument document = ProviderAdminDocument.builder()
                .providerId(providerId)
                .type(dto.getType())
                .fileName(dto.getFileName())
                .fileUrl(fileUrl)
                .filePath(filePath)
                .documentNumber(dto.getDocumentNumber())
                .expiryDate(dto.getExpiryDate())
                .notes(dto.getNotes())
                .uploadedAt(LocalDateTime.now())
                .build();
        
        ProviderAdminDocument saved = documentRepository.save(document);
        log.info("Document created with ID: {}", saved.getId());
        
        return mapToDto(saved);
    }
    
    /**
     * Delete provider document
     */
    @Transactional
    public void deleteDocument(Long providerId, Long docId) {
        log.info("Deleting document {} for provider {}", docId, providerId);
        assertCanAccessProvider(providerId);
        
        ProviderAdminDocument document = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", docId));
        
        // Security check: ensure document belongs to provider
        if (!document.getProviderId().equals(providerId)) {
            throw new IllegalStateException("Document does not belong to this provider");
        }
        
        // Delete file from storage
        if (document.getFilePath() != null) {
            try {
                // Extract fileKey from filePath
                // FilePath format: /path/to/uploads/provider-documents/uuid_filename
                // FileKey format: provider-documents/uuid_filename
                String fileKey = document.getFilePath();
                if (fileKey.contains("/uploads/")) {
                    fileKey = fileKey.substring(fileKey.indexOf("/uploads/") + 9);
                }
                fileStorageService.delete(fileKey);
                log.debug("File deleted from storage: {}", fileKey);
            } catch (Exception e) {
                log.warn("Failed to delete file from storage: {}", e.getMessage());
                // Continue with database deletion even if file deletion fails
            }
        }
        
        documentRepository.delete(document);
        log.info("Document deleted successfully");
    }
    
    /**
     * Map entity to DTO
     */
    private ProviderAdminDocumentResponseDto mapToDto(ProviderAdminDocument entity) {
        return ProviderAdminDocumentResponseDto.builder()
                .id(entity.getId())
                .providerId(entity.getProviderId())
                .type(entity.getType())
                .typeLabel(ProviderAdminDocument.getTypeLabel(entity.getType()))
                .fileName(entity.getFileName())
                .fileUrl(entity.getFileUrl())
                .documentNumber(entity.getDocumentNumber())
                .expiryDate(entity.getExpiryDate())
                .notes(entity.getNotes())
                .uploadedAt(entity.getUploadedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void assertCanAccessProvider(Long providerId) {
        var currentUser = authorizationService.requireCurrentUser();
        if (!authorizationService.canAccessProvider(currentUser, providerId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access to provider administrative documents denied");
        }
    }
}

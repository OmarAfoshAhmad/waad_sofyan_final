package com.waad.tba.modules.provider.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import com.waad.tba.common.file.LocalFileStorageService;
import com.waad.tba.modules.provider.dto.ProviderAdminDocumentCreateDto;
import com.waad.tba.modules.provider.entity.ProviderAdminDocument;
import com.waad.tba.modules.provider.repository.ProviderAdminDocumentRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

@ExtendWith(MockitoExtension.class)
class ProviderAdminDocumentServiceSecurityTest {

    @Mock private ProviderAdminDocumentRepository documentRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private LocalFileStorageService fileStorageService;
    @Mock private AuthorizationService authorizationService;
    @Mock private MultipartFile file;

    private ProviderAdminDocumentService service;
    private User currentUser;

    @BeforeEach
    void setUp() {
        service = new ProviderAdminDocumentService(
                documentRepository,
                providerRepository,
                fileStorageService,
                authorizationService);
        currentUser = User.builder().userType("PROVIDER_STAFF").providerId(20L).build();
        when(authorizationService.requireCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void unrelatedProviderCannotListAdministrativeDocuments() {
        when(authorizationService.canAccessProvider(currentUser, 21L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.getDocumentsByProviderId(21L));

        verify(providerRepository, never()).existsById(21L);
        verify(documentRepository, never()).findByProviderId(21L);
    }

    @Test
    void deniedCreateDoesNotUploadOrPersistAFile() {
        when(authorizationService.canAccessProvider(currentUser, 21L)).thenReturn(false);
        ProviderAdminDocumentCreateDto dto = new ProviderAdminDocumentCreateDto();

        assertThrows(AccessDeniedException.class, () -> service.createDocument(21L, dto, file));

        verify(fileStorageService, never()).upload(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
        verify(documentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void documentIdMustBelongToProviderInRequestPathBeforeStorageMutation() {
        when(authorizationService.canAccessProvider(currentUser, 20L)).thenReturn(true);
        when(documentRepository.findById(99L)).thenReturn(Optional.of(
                ProviderAdminDocument.builder()
                        .id(99L)
                        .providerId(21L)
                        .filePath("provider-documents/secret.pdf")
                        .build()));

        assertThrows(IllegalStateException.class, () -> service.deleteDocument(20L, 99L));

        verify(fileStorageService, never()).delete(org.mockito.ArgumentMatchers.anyString());
        verify(documentRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}

package com.waad.tba.modules.preauthorization.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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

import com.waad.tba.common.file.LocalFileStorageService;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationAttachment;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationAttachmentRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

@ExtendWith(MockitoExtension.class)
class PreAuthorizationAttachmentServiceSecurityTest {

    @Mock private PreAuthorizationAttachmentRepository attachmentRepository;
    @Mock private PreAuthorizationRepository preAuthorizationRepository;
    @Mock private LocalFileStorageService fileStorageService;
    @Mock private AuthorizationService authorizationService;

    private PreAuthorizationAttachmentService service;
    private User currentUser;

    @BeforeEach
    void setUp() {
        service = new PreAuthorizationAttachmentService(
                attachmentRepository,
                preAuthorizationRepository,
                fileStorageService,
                authorizationService);
        currentUser = mock(User.class);
        when(authorizationService.requireCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void downloadMustFailBeforeAttachmentOrStorageReadForUnrelatedProvider() {
        currentUser = User.builder().userType("PROVIDER_STAFF").providerId(20L).build();
        when(authorizationService.requireCurrentUser()).thenReturn(currentUser);
        when(authorizationService.isProvider(currentUser)).thenReturn(true);
        when(preAuthorizationRepository.findById(10L))
                .thenReturn(Optional.of(PreAuthorization.builder().id(10L).providerId(21L).build()));

        assertThrows(AccessDeniedException.class, () -> service.downloadAttachment(10L, 99L));

        verify(attachmentRepository, never()).findById(99L);
        verify(fileStorageService, never()).download(anyString());
    }

    @Test
    void attachmentIdMustBelongToPreAuthorizationInRequestPath() {
        when(authorizationService.isInternalStaff(currentUser)).thenReturn(true);
        when(preAuthorizationRepository.findById(10L))
                .thenReturn(Optional.of(PreAuthorization.builder().id(10L).build()));
        when(attachmentRepository.findById(99L)).thenReturn(Optional.of(
                PreAuthorizationAttachment.builder()
                        .id(99L)
                        .preAuthorizationId(11L)
                        .filePath("pre-authorizations/11/report.pdf")
                        .build()));

        assertThrows(IllegalArgumentException.class, () -> service.downloadAttachment(10L, 99L));

        verify(fileStorageService, never()).download(anyString());
    }

    @Test
    void deleteMustFailBeforeStorageOrMetadataMutationForUnrelatedProvider() {
        currentUser = User.builder().userType("PROVIDER_STAFF").providerId(20L).build();
        when(authorizationService.requireCurrentUser()).thenReturn(currentUser);
        when(authorizationService.isProvider(currentUser)).thenReturn(true);
        when(preAuthorizationRepository.findById(10L))
                .thenReturn(Optional.of(PreAuthorization.builder().id(10L).providerId(21L).build()));

        assertThrows(AccessDeniedException.class, () -> service.deleteAttachment(10L, 99L));

        verify(attachmentRepository, never()).findById(99L);
        verify(fileStorageService, never()).delete(anyString());
        verify(attachmentRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void providerCanDownloadAttachmentFromOwnPreAuthorization() {
        currentUser = User.builder().userType("PROVIDER_STAFF").providerId(20L).build();
        when(authorizationService.requireCurrentUser()).thenReturn(currentUser);
        when(authorizationService.isProvider(currentUser)).thenReturn(true);
        when(preAuthorizationRepository.findById(10L))
                .thenReturn(Optional.of(PreAuthorization.builder().id(10L).providerId(20L).build()));
        when(attachmentRepository.findById(99L)).thenReturn(Optional.of(
                PreAuthorizationAttachment.builder()
                        .id(99L)
                        .preAuthorizationId(10L)
                        .filePath("pre-authorizations/10/report.pdf")
                        .build()));
        when(fileStorageService.download("pre-authorizations/10/report.pdf"))
                .thenReturn(new byte[] { 1, 2, 3 });

        service.downloadAttachment(10L, 99L);

        verify(fileStorageService).download("pre-authorizations/10/report.pdf");
    }
}

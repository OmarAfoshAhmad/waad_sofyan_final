package com.waad.tba.modules.visit.service;

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

import com.waad.tba.common.file.FileStorageService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitAttachment;
import com.waad.tba.modules.visit.repository.VisitAttachmentRepository;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.security.AuthorizationService;

@ExtendWith(MockitoExtension.class)
class VisitAttachmentServiceSecurityTest {

    @Mock private VisitAttachmentRepository attachmentRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuthorizationService authorizationService;

    private VisitAttachmentService service;
    private User currentUser;

    @BeforeEach
    void setUp() {
        service = new VisitAttachmentService(
                attachmentRepository,
                visitRepository,
                fileStorageService,
                authorizationService);
        currentUser = mock(User.class);
        when(authorizationService.requireCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void downloadMustFailBeforeAttachmentOrStorageReadWhenUserCannotAccessVisit() {
        when(authorizationService.canAccessVisit(currentUser, 10L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.downloadAttachment(10L, 99L));

        verify(attachmentRepository, never()).findById(99L);
        verify(fileStorageService, never()).download(anyString());
    }

    @Test
    void attachmentIdMustBelongToVisitInRequestPath() {
        when(authorizationService.canAccessVisit(currentUser, 10L)).thenReturn(true);
        VisitAttachment attachment = VisitAttachment.builder()
                .id(99L)
                .visit(Visit.builder().id(11L).build())
                .fileKey("visits/11/report.pdf")
                .build();
        when(attachmentRepository.findById(99L)).thenReturn(Optional.of(attachment));

        assertThrows(RuntimeException.class, () -> service.downloadAttachment(10L, 99L));

        verify(fileStorageService, never()).download(anyString());
    }

    @Test
    void deleteMustFailBeforeStorageMutationWhenUserCannotAccessVisit() {
        when(authorizationService.canAccessVisit(currentUser, 10L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.deleteAttachment(10L, 99L));

        verify(attachmentRepository, never()).findById(99L);
        verify(fileStorageService, never()).delete(anyString());
        verify(attachmentRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}

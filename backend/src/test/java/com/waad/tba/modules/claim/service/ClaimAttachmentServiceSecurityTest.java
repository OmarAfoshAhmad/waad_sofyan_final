package com.waad.tba.modules.claim.service;

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

import com.waad.tba.common.file.FileStorageService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimAttachment;
import com.waad.tba.modules.claim.repository.ClaimAttachmentRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

@ExtendWith(MockitoExtension.class)
class ClaimAttachmentServiceSecurityTest {

    @Mock private ClaimAttachmentRepository attachmentRepository;
    @Mock private ClaimRepository claimRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuthorizationService authorizationService;

    private ClaimAttachmentService service;
    private User currentUser;

    @BeforeEach
    void setUp() {
        service = new ClaimAttachmentService(
                attachmentRepository,
                claimRepository,
                fileStorageService,
                authorizationService);
        currentUser = User.builder().id(7L).username("provider-user").userType("PROVIDER_STAFF").build();
        when(authorizationService.requireCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void downloadMustFailBeforeStorageReadWhenUserCannotAccessClaim() {
        when(authorizationService.canAccessClaim(currentUser, 10L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.downloadAttachment(10L, 99L));

        verify(attachmentRepository, never()).findById(99L);
        verify(fileStorageService, never()).download(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void attachmentIdMustBelongToClaimInRequestPath() {
        when(authorizationService.canAccessClaim(currentUser, 10L)).thenReturn(true);
        Claim anotherClaim = Claim.builder().id(11L).build();
        ClaimAttachment attachment = ClaimAttachment.builder()
                .id(99L)
                .claim(anotherClaim)
                .fileKey("claims/11/secret.pdf")
                .build();
        when(attachmentRepository.findById(99L)).thenReturn(Optional.of(attachment));

        assertThrows(RuntimeException.class, () -> service.downloadAttachment(10L, 99L));

        verify(fileStorageService, never()).download(org.mockito.ArgumentMatchers.anyString());
    }
}

package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for SECTION_02 CRITICAL finding #1: deleteClaim (and,
 * by the same gap, restoreClaim) performed no ownership check at all — any
 * role permitted to call the endpoint could soft-delete/restore ANY claim by
 * id. The fix adds the same authorizationService.canAccessClaim +
 * reviewerIsolationService.validateReviewerAccess check already used by
 * getClaim/updateClaim elsewhere in this class.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDeleteRestoreSecurityTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private ClaimService claimService;

    private User currentUser;
    private Claim claim;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(9L).username("outside-reviewer").userType("MEDICAL_REVIEWER").build();
        claim = Claim.builder().id(951L).providerId(251L).active(true).build();

        when(authorizationService.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void deleteClaimDeniedWhenCallerCannotAccessClaim() {
        when(claimRepository.findByIdForUpdate(951L)).thenReturn(Optional.of(claim));
        when(authorizationService.canAccessClaim(currentUser, 951L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> claimService.deleteClaim(951L, "test"));

        verify(claimRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restoreClaimDeniedWhenCallerCannotAccessClaim() {
        Claim deletedClaim = Claim.builder().id(951L).providerId(251L).active(false).build();
        when(claimRepository.findById(951L)).thenReturn(Optional.of(deletedClaim));
        when(authorizationService.canAccessClaim(currentUser, 951L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> claimService.restoreClaim(951L));

        verify(claimRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}

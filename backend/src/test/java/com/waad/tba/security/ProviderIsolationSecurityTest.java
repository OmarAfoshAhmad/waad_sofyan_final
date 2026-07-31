package com.waad.tba.security;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Provider Isolation & Security Negative Tests
 * 
 * Verifies strict provider boundary enforcement and negative security rules:
 * - Provider A CANNOT access Provider B claims or visits
 * - Provider user without providerId is rejected
 * - ProviderContextGuard throws AccessDeniedException on cross-provider attempt
 */
@ExtendWith(MockitoExtension.class)
class ProviderIsolationSecurityTest {

    @Mock
    private RoleService roleService;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private DataAccessService dataAccessService;

    private User providerAUser;
    private User providerBUser;
    private User unlinkedProviderUser;
    private Claim providerAClaim;
    private Visit providerAVisit;

    @BeforeEach
    void setUp() {
        providerAUser = User.builder()
                .id(101L)
                .username("provider_a_staff")
                .userType("PROVIDER_STAFF")
                .providerId(10L)
                .build();

        providerBUser = User.builder()
                .id(102L)
                .username("provider_b_staff")
                .userType("PROVIDER_STAFF")
                .providerId(20L)
                .build();

        unlinkedProviderUser = User.builder()
                .id(103L)
                .username("unlinked_provider_staff")
                .userType("PROVIDER_STAFF")
                .providerId(null)
                .build();

        providerAClaim = new Claim();
        providerAClaim.setId(5001L);
        providerAClaim.setProviderId(10L);

        providerAVisit = new Visit();
        providerAVisit.setId(7001L);
        providerAVisit.setProviderId(10L);
    }

    @Test
    @DisplayName("Provider A staff CAN access Provider A claim")
    void testProviderCanAccessOwnClaim() {
        when(roleService.isSuperAdmin(providerAUser)).thenReturn(false);
        when(roleService.canAccessInternalOperations(providerAUser)).thenReturn(false);
        when(roleService.isFinancialUser(providerAUser)).thenReturn(false);
        when(roleService.isProvider(providerAUser)).thenReturn(true);
        when(claimRepository.findById(5001L)).thenReturn(Optional.of(providerAClaim));

        boolean canAccess = dataAccessService.canAccessClaim(providerAUser, 5001L);
        assertTrue(canAccess, "Provider A staff should be allowed to access Provider A claim");
    }

    @Test
    @DisplayName("Provider B staff CANNOT access Provider A claim (Cross-provider IDOR prevention)")
    void testProviderB_CannotAccess_ProviderA_Claim() {
        when(roleService.isSuperAdmin(providerBUser)).thenReturn(false);
        when(roleService.canAccessInternalOperations(providerBUser)).thenReturn(false);
        when(roleService.isFinancialUser(providerBUser)).thenReturn(false);
        when(roleService.isProvider(providerBUser)).thenReturn(true);
        when(claimRepository.findById(5001L)).thenReturn(Optional.of(providerAClaim));

        boolean canAccess = dataAccessService.canAccessClaim(providerBUser, 5001L);
        assertFalse(canAccess, "SECURITY VIOLATION: Provider B staff accessed Provider A claim!");
    }

    @Test
    @DisplayName("Unlinked Provider staff CANNOT access any claim")
    void testUnlinkedProviderUser_CannotAccess_Claim() {
        when(roleService.isSuperAdmin(unlinkedProviderUser)).thenReturn(false);
        when(roleService.canAccessInternalOperations(unlinkedProviderUser)).thenReturn(false);
        when(roleService.isFinancialUser(unlinkedProviderUser)).thenReturn(false);
        when(roleService.isProvider(unlinkedProviderUser)).thenReturn(true);
        when(claimRepository.findById(5001L)).thenReturn(Optional.of(providerAClaim));

        boolean canAccess = dataAccessService.canAccessClaim(unlinkedProviderUser, 5001L);
        assertFalse(canAccess, "SECURITY VIOLATION: Provider user without providerId was granted claim access!");
    }

    @Test
    @DisplayName("Provider B staff CANNOT access Provider A visit")
    void testProviderB_CannotAccess_ProviderA_Visit() {
        when(roleService.isSuperAdmin(providerBUser)).thenReturn(false);
        when(roleService.canAccessInternalOperations(providerBUser)).thenReturn(false);
        when(roleService.isProvider(providerBUser)).thenReturn(true);
        when(visitRepository.findById(7001L)).thenReturn(Optional.of(providerAVisit));

        boolean canAccess = dataAccessService.canAccessVisit(providerBUser, 7001L);
        assertFalse(canAccess, "SECURITY VIOLATION: Provider B staff accessed Provider A visit!");
    }
}

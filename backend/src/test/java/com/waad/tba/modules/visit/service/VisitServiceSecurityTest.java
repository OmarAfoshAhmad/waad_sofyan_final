package com.waad.tba.modules.visit.service;

import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.visit.dto.VisitCreateDto;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.repository.VisitRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a CRITICAL IDOR found during SECTION_02's audit of
 * remaining modules: VisitService.update/delete performed no ownership check
 * at all (unlike findById, which correctly calls canAccessVisit) — any
 * authorized role could mutate/delete another provider's visit by ID. The
 * deprecated, unscoped search() (which returned every employer/provider's
 * visits to any of 4 broad roles) is now retired outright rather than fixed,
 * since findAllPaginated already provides the scoped equivalent.
 */
@ExtendWith(MockitoExtension.class)
class VisitServiceSecurityTest {

    @Mock
    private VisitRepository repository;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private VisitService visitService;

    private User currentUser;
    private Visit visit;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(9L).username("outside-user").userType("MEDICAL_REVIEWER").build();
        visit = Visit.builder().id(700L).providerId(251L).build();
        lenient().when(authorizationService.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void updateDeniedWhenCallerCannotAccessVisit() {
        lenient().when(authorizationService.canAccessVisit(currentUser, 700L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> visitService.update(700L, new VisitCreateDto()));

        verify(repository, never()).findById(700L);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteDeniedWhenCallerCannotAccessVisit() {
        when(repository.existsById(700L)).thenReturn(true);
        lenient().when(authorizationService.canAccessVisit(currentUser, 700L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> visitService.delete(700L));

        verify(repository, never()).deleteById(700L);
    }

    @Test
    void deprecatedSearchIsRetiredAndFailsClosed() {
        assertThrows(AccessDeniedException.class, () -> visitService.search("anything"));
    }
}

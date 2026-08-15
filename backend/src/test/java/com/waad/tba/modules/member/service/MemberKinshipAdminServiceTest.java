package com.waad.tba.modules.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.member.security.MemberCommandAccessPolicy;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.security.AuthorizationService;

@ExtendWith(MockitoExtension.class)
class MemberKinshipAdminServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock AuditLogService audit;
    @Mock AuthorizationService authorization;
    @Mock MemberCommandAccessPolicy policy;

    private MemberKinshipAdminService service;

    @BeforeEach
    void setUp() {
        service = new MemberKinshipAdminService(jdbc, audit, authorization, policy);
    }

    @Test
    void requiresAReasonBeforeAuthorizationOrWrite() {
        assertThrows(BusinessRuleException.class, () -> service.resetVerification("  "));
        verify(policy, never()).require(any(), any());
        verify(jdbc, never()).update(any(String.class));
    }

    @Test
    void denialOccursBeforeTheSystemWideUpdate() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(policy).require(MemberOperation.RESET_KINSHIP, null);

        assertThrows(AccessDeniedException.class, () -> service.resetVerification("تصحيح البيانات"));

        verify(jdbc, never()).update(any(String.class));
        verify(audit, never()).createAuditLog(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void writesTheResetAndDurableAuditInThatOrder() {
        User actor = User.builder().id(9L).username("root").build();
        when(authorization.getCurrentUser()).thenReturn(actor);
        when(jdbc.update(any(String.class))).thenReturn(17);

        int updated = service.resetVerification("  إعادة تدقيق الأسر  ");

        assertEquals(17, updated);
        var ordered = inOrder(policy, jdbc, audit);
        ordered.verify(policy).require(MemberOperation.RESET_KINSHIP, null);
        ordered.verify(jdbc).update(any(String.class));
        ordered.verify(audit).createAuditLog(eq("RESET_KINSHIP"), eq("SYSTEM_SETTING"), eq(null),
                org.mockito.ArgumentMatchers.contains("إعادة تدقيق الأسر"), eq(9L), eq("root"),
                eq(null), eq(null));
    }
}

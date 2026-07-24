package com.waad.tba.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceTest {

    @Mock
    private SecurityAuditEventRepository repository;

    private SecurityAuditService auditService;

    @BeforeEach
    void setup() {
        ObjectMapper objectMapper = new ObjectMapper();
        auditService = new SecurityAuditService(repository, objectMapper);
    }

    @Test
    void testLogLoginSuccess() {
        Long userId = 1L;
        String username = "testuser";
        String ip = "192.168.1.1";
        String userAgent = "Mozilla/5.0";

        SecurityAuditEvent mockEvent = SecurityAuditEvent.builder()
                .id(1L)
                .actorId(userId)
                .actorUsername(username)
                .actionType(SecurityAuditEvent.AuditActionType.LOGIN_SUCCESS)
                .result(SecurityAuditEvent.AuditResult.SUCCESS)
                .requestIp(ip)
                .userAgent(userAgent)
                .correlationId("test-correlation-id")
                .build();

        when(repository.save(any(SecurityAuditEvent.class))).thenReturn(mockEvent);

        auditService.logLoginSuccess(userId, username, ip, userAgent);

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).save(captor.capture());

        SecurityAuditEvent saved = captor.getValue();
        assertThat(saved.getActionType()).isEqualTo(SecurityAuditEvent.AuditActionType.LOGIN_SUCCESS);
        assertThat(saved.getResult()).isEqualTo(SecurityAuditEvent.AuditResult.SUCCESS);
        assertThat(saved.getActorUsername()).isEqualTo(username);
    }

    @Test
    void testLogLoginFailure() {
        String username = "wronguser";
        String ip = "192.168.1.2";
        String userAgent = "Mozilla";
        String reason = "Invalid password";

        when(repository.save(any(SecurityAuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        auditService.logLoginFailure(username, ip, userAgent, reason);

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).save(captor.capture());

        SecurityAuditEvent saved = captor.getValue();
        assertThat(saved.getResult()).isEqualTo(SecurityAuditEvent.AuditResult.DENIED);
        assertThat(saved.getActionType()).isEqualTo(SecurityAuditEvent.AuditActionType.LOGIN_FAILED);
    }

    @Test
    void testNoPasswordsInAudit() {
        when(repository.save(any(SecurityAuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        auditService.logSecurityEvent(
                1L, "user",
                SecurityAuditEvent.AuditActionType.PASSWORD_CHANGED,
                "USER", 1L, "user",
                "192.168.1.1", "Mozilla",
                SecurityAuditEvent.AuditResult.SUCCESS,
                "Password changed",
                null, null
        );

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).save(captor.capture());

        SecurityAuditEvent saved = captor.getValue();
        assertThat(saved.getBeforeState()).isNull();
        assertThat(saved.getAfterState()).isNull();
    }

    @Test
    void testCorrelationIdUnique() {
        when(repository.save(any(SecurityAuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        auditService.logLoginSuccess(1L, "user1", "1.1.1.1", "Mozilla");
        auditService.logLoginSuccess(2L, "user2", "2.2.2.2", "Chrome");

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());

        java.util.List<SecurityAuditEvent> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getCorrelationId()).isNotNull();
        assertThat(saved.get(1).getCorrelationId()).isNotNull();
        assertThat(saved.get(0).getCorrelationId()).isNotEqualTo(saved.get(1).getCorrelationId());
    }

    @Test
    void testAccountLockedAudit() {
        when(repository.save(any(SecurityAuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        auditService.logAccountLocked(1L, "testuser", "192.168.1.1");

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).save(captor.capture());

        SecurityAuditEvent saved = captor.getValue();
        assertThat(saved.getActionType()).isEqualTo(SecurityAuditEvent.AuditActionType.ACCOUNT_LOCKED);
        assertThat(saved.getResult()).isEqualTo(SecurityAuditEvent.AuditResult.SUCCESS);
    }

    @Test
    void testPasswordChangedAudit() {
        Long userId = 5L;
        String username = "changepassword";

        when(repository.save(any(SecurityAuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        auditService.logPasswordChanged(userId, username, "10.0.0.1", "Safari");

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).save(captor.capture());

        SecurityAuditEvent saved = captor.getValue();
        assertThat(saved.getActionType()).isEqualTo(SecurityAuditEvent.AuditActionType.PASSWORD_CHANGED);
        assertThat(saved.getTargetId()).isEqualTo(userId);
    }

    @Test
    void testEventTimestampSet() {
        when(repository.save(any(SecurityAuditEvent.class)))
                .thenAnswer(inv -> {
                    SecurityAuditEvent event = inv.getArgument(0);
                    if (event.getCreatedAt() == null) {
                        event.setCreatedAt(java.time.LocalDateTime.now());
                    }
                    return event;
                });

        auditService.logLoginSuccess(1L, "user", "1.1.1.1", "Firefox");

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).save(captor.capture());

        SecurityAuditEvent saved = captor.getValue();
        assertThat(saved.getEventTimestamp()).isNotNull();
    }

    @Test
    void testFileAccessDeniedAudit() {
        when(repository.save(any(SecurityAuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        auditService.logFileAccessDenied(1L, "user", "sensitive.pdf", "Unauthorized access");

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).save(captor.capture());

        SecurityAuditEvent saved = captor.getValue();
        assertThat(saved.getActionType()).isEqualTo(SecurityAuditEvent.AuditActionType.FILE_ACCESS_DENIED);
        assertThat(saved.getTargetType()).isEqualTo("FILE");
        assertThat(saved.getTargetIdentifier()).isEqualTo("sensitive.pdf");
        assertThat(saved.getResult()).isEqualTo(SecurityAuditEvent.AuditResult.DENIED);
    }
}

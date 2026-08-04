package com.waad.tba.modules.errorlog.service;

import com.waad.tba.modules.errorlog.dto.ErrorLogDtos.ErrorLogDetailDto;
import com.waad.tba.modules.errorlog.entity.ErrorLogSource;
import com.waad.tba.modules.errorlog.entity.SystemErrorLog;
import com.waad.tba.modules.errorlog.repository.SystemErrorLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemErrorLogServiceTest {

    @Mock
    private SystemErrorLogRepository repository;

    private SystemErrorLogService service;

    @BeforeEach
    void setUp() {
        service = new SystemErrorLogService(repository);
    }

    @Test
    @DisplayName("record() redacts secrets from technical text before persisting")
    void recordRedactsSecrets() {
        when(repository.save(any(SystemErrorLog.class))).thenAnswer(i -> i.getArgument(0));
        SystemErrorLog event = SystemErrorLog.builder()
                .source(ErrorLogSource.BACKEND)
                .technicalMessage("failed with password=SuperSecret1 and token=abc.def.ghi")
                .stackExcerpt("Authorization: Bearer abc123 at com.example")
                .build();

        service.record(event);

        ArgumentCaptor<SystemErrorLog> captor = ArgumentCaptor.forClass(SystemErrorLog.class);
        verify(repository).save(captor.capture());
        SystemErrorLog saved = captor.getValue();
        assertFalse(saved.getTechnicalMessage().contains("SuperSecret1"), "password value must be redacted");
        assertFalse(saved.getTechnicalMessage().contains("abc.def.ghi"), "token value must be redacted");
        assertFalse(saved.getStackExcerpt().toLowerCase().contains("bearer abc123"), "bearer token must be redacted");
        assertNotNull(saved.getOccurredAt());
        assertNotNull(saved.getStackHash());
    }

    @Test
    @DisplayName("record() truncates over-long fields to fit the columns")
    void recordTruncatesLongFields() {
        when(repository.save(any(SystemErrorLog.class))).thenAnswer(i -> i.getArgument(0));
        String huge = "x".repeat(5000);
        service.record(SystemErrorLog.builder().source(ErrorLogSource.FRONTEND).stackExcerpt(huge).build());

        ArgumentCaptor<SystemErrorLog> captor = ArgumentCaptor.forClass(SystemErrorLog.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().getStackExcerpt().length() <= 4000);
    }

    @Test
    @DisplayName("record() never throws even when the repository fails")
    void recordSwallowsRepositoryFailure() {
        when(repository.save(any(SystemErrorLog.class))).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() ->
                service.record(SystemErrorLog.builder().source(ErrorLogSource.BACKEND).build()));
    }

    @Test
    @DisplayName("resolve() stamps resolver and time when marking resolved")
    void resolveStampsResolver() {
        SystemErrorLog stored = SystemErrorLog.builder().id(5L).source(ErrorLogSource.BACKEND).resolved(false).build();
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        when(repository.save(any(SystemErrorLog.class))).thenAnswer(i -> i.getArgument(0));

        ErrorLogDetailDto result = service.resolve(5L, true, "handled", "admin");

        assertTrue(result.resolved());
        assertEquals("admin", result.resolvedBy());
        assertNotNull(result.resolvedAt());
        assertEquals("handled", result.notes());
    }
}

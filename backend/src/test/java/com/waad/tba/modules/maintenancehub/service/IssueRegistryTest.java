package com.waad.tba.modules.maintenancehub.service;

import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueRegistration;
import com.waad.tba.modules.maintenancehub.entity.IssueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueRegistryTest {

    @Mock
    private IssueRegistryWriter writer;

    private IssueRegistry registry;

    private static IssueRegistration registration() {
        return new IssueRegistration(IssueType.BACKEND_ERROR, "fp-1", null, "API", "/x",
                "title", "desc", "HIGH", "rule", null);
    }

    @Test
    @DisplayName("A successful write returns the issue id")
    void successReturnsId() {
        registry = new IssueRegistry(writer);
        when(writer.register(any())).thenReturn(42L);

        Long id = registry.register(registration());

        assertThat(id).isEqualTo(42L);
        verify(writer).register(any());
    }

    @Test
    @DisplayName("A failing writer never propagates — the caller's own work must not break")
    void writerFailureIsSwallowed() {
        registry = new IssueRegistry(writer);
        when(writer.register(any())).thenThrow(new RuntimeException("db down"));

        Long id = registry.register(registration());

        assertThat(id).isNull();
    }

    @Test
    @DisplayName("A null registration never throws out of register()")
    void nullRegistrationIsSwallowed() {
        registry = new IssueRegistry(writer);
        when(writer.register(isNull())).thenThrow(new IllegalArgumentException("Issue fingerprint is required"));

        Long id = registry.register(null);

        assertThat(id).isNull();
    }
}

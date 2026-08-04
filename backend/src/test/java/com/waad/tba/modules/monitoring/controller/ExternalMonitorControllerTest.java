package com.waad.tba.modules.monitoring.controller;

import com.waad.tba.modules.monitoring.controller.ExternalMonitorController.HeartbeatRequest;
import com.waad.tba.modules.monitoring.service.MonitoringSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalMonitorControllerTest {

    @Mock
    private MonitoringSettingsService settingsService;
    @Mock
    private Environment environment;

    private ExternalMonitorController controller;

    @BeforeEach
    void setUp() {
        controller = new ExternalMonitorController(settingsService, environment);
    }

    @Test
    @DisplayName("Heartbeat is recorded when no shared token is configured")
    void recordsWhenNoTokenConfigured() {
        when(environment.getProperty("WAAD_MONITOR_HEARTBEAT_TOKEN")).thenReturn(null);

        var response = controller.heartbeat(new HeartbeatRequest("external-monitor", "UP"), null);

        assertEquals(200, response.getStatusCode().value());
        verify(settingsService).recordExternalHeartbeat("external-monitor", "UP");
    }

    @Test
    @DisplayName("Heartbeat is rejected with 401 when token does not match")
    void rejectsWhenTokenMismatch() {
        when(environment.getProperty("WAAD_MONITOR_HEARTBEAT_TOKEN")).thenReturn("expected-secret");

        var response = controller.heartbeat(new HeartbeatRequest("external-monitor", "UP"), "wrong");

        assertEquals(401, response.getStatusCode().value());
        verify(settingsService, never()).recordExternalHeartbeat(anyString(), anyString());
    }

    @Test
    @DisplayName("Heartbeat is recorded when the token matches")
    void recordsWhenTokenMatches() {
        when(environment.getProperty("WAAD_MONITOR_HEARTBEAT_TOKEN")).thenReturn("expected-secret");

        var response = controller.heartbeat(new HeartbeatRequest("external-monitor", "DEGRADED"), "expected-secret");

        assertEquals(200, response.getStatusCode().value());
        verify(settingsService).recordExternalHeartbeat("external-monitor", "DEGRADED");
    }
}

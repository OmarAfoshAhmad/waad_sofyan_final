package com.waad.tba.modules.monitoring.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.monitoring.service.MonitoringSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Public heartbeat endpoint for a standalone external monitor running outside this
 * backend, so it can still alert when the backend itself is down.
 *
 * Auth: intentionally NOT behind login (the external monitor has no session). If
 * WAAD_MONITOR_HEARTBEAT_TOKEN is configured, a matching X-Monitor-Token header is
 * required, compared in constant time to avoid a timing side-channel on the token.
 */
@RestController
@RequestMapping("/api/v1/system/monitoring")
@RequiredArgsConstructor
public class ExternalMonitorController {

    private final MonitoringSettingsService settingsService;
    private final Environment environment;

    public record HeartbeatRequest(String source, String status) {
    }

    @PostMapping("/external-heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @RequestBody(required = false) HeartbeatRequest request,
            @RequestHeader(value = "X-Monitor-Token", required = false) String token) {
        String expected = environment.getProperty("WAAD_MONITOR_HEARTBEAT_TOKEN");
        if (expected != null && !expected.isBlank() && !constantTimeEquals(expected, token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid monitor token"));
        }
        String source = request == null ? null : request.source();
        String status = request == null ? null : request.status();
        settingsService.recordExternalHeartbeat(source, status);
        return ResponseEntity.ok(ApiResponse.success(null, "Heartbeat recorded", "تم استلام نبضة المراقب الخارجي"));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}

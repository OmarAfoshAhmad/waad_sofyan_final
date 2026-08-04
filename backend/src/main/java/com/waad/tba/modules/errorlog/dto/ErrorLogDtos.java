package com.waad.tba.modules.errorlog.dto;

import com.waad.tba.modules.errorlog.entity.ErrorLogSeverity;
import com.waad.tba.modules.errorlog.entity.ErrorLogSource;

import java.time.LocalDateTime;

public final class ErrorLogDtos {
    private ErrorLogDtos() {
    }

    /** Compact row for the admin table (no full stack trace). */
    public record ErrorLogRowDto(
            Long id,
            LocalDateTime occurredAt,
            ErrorLogSource source,
            ErrorLogSeverity severity,
            String environment,
            String correlationId,
            String username,
            String role,
            String httpMethod,
            String path,
            Integer statusCode,
            String errorCode,
            String userMessage,
            String frontendRoute,
            Boolean resolved,
            String resolvedBy,
            LocalDateTime resolvedAt
    ) {
    }

    /** Full detail (includes technical message + stack excerpt for SUPER_ADMIN). */
    public record ErrorLogDetailDto(
            Long id,
            LocalDateTime occurredAt,
            ErrorLogSource source,
            ErrorLogSeverity severity,
            String environment,
            String correlationId,
            Long userId,
            String username,
            String role,
            String httpMethod,
            String path,
            Integer statusCode,
            String errorCode,
            String userMessage,
            String technicalMessage,
            String exceptionClass,
            String stackExcerpt,
            String stackHash,
            String frontendRoute,
            String browser,
            Boolean resolved,
            String resolvedBy,
            LocalDateTime resolvedAt,
            String notes
    ) {
    }

    /** Payload the frontend posts when a UI error is shown to a user. */
    public record FrontendErrorRequest(
            String severity,
            String correlationId,
            String userMessage,
            String technicalMessage,
            String errorCode,
            Integer statusCode,
            String path,
            String frontendRoute,
            String stackExcerpt
    ) {
    }

    public record ResolveErrorRequest(
            Boolean resolved,
            String notes
    ) {
    }
}

package com.waad.tba.modules.errorlog.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.errorlog.dto.ErrorLogDtos.ErrorLogDetailDto;
import com.waad.tba.modules.errorlog.dto.ErrorLogDtos.ErrorLogRowDto;
import com.waad.tba.modules.errorlog.dto.ErrorLogDtos.FrontendErrorRequest;
import com.waad.tba.modules.errorlog.dto.ErrorLogDtos.ResolveErrorRequest;
import com.waad.tba.modules.errorlog.entity.ErrorLogSeverity;
import com.waad.tba.modules.errorlog.entity.ErrorLogSource;
import com.waad.tba.modules.errorlog.entity.SystemErrorLog;
import com.waad.tba.modules.errorlog.service.SystemErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Admin API for the internal error log.
 *
 * Reading and resolving is SUPER_ADMIN only. The /frontend ingest endpoint is open to
 * any authenticated user so their UI can report errors they saw — identity fields are
 * always taken from the authenticated session, never trusted from the request body.
 */
@RestController
@RequestMapping("/api/v1/system-errors")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SystemErrorLogController {

    private final SystemErrorLogService service;
    private final Environment environment;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Page<ErrorLogRowDto>> list(
            @RequestParam(required = false) ErrorLogSource source,
            @RequestParam(required = false) ErrorLogSeverity severity,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return ApiResponse.success(
                service.list(source, severity, resolved, statusCode, path, username, from, to, pageable));
    }

    @GetMapping("/unresolved-count")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Long> unresolvedCount() {
        return ApiResponse.success(service.unresolvedCount());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<ErrorLogDetailDto> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<ErrorLogDetailDto> resolve(@PathVariable Long id, @RequestBody ResolveErrorRequest request,
                                                  Authentication authentication) {
        boolean resolved = request == null || request.resolved() == null || request.resolved();
        String notes = request == null ? null : request.notes();
        return ApiResponse.success(service.resolve(id, resolved, notes, usernameOf(authentication)),
                "Error event updated", "تم تحديث حالة الخطأ");
    }

    /** Ingest a frontend error that was shown to a user. Available to any authenticated user. */
    @PostMapping("/frontend")
    public ApiResponse<Void> reportFrontendError(@RequestBody FrontendErrorRequest request,
                                                 Authentication authentication,
                                                 HttpServletRequest httpRequest) {
        String username = authentication == null ? null : authentication.getName();
        String role = authentication == null || authentication.getAuthorities() == null ? null
                : authentication.getAuthorities().stream().map(Object::toString).findFirst().orElse(null);
        SystemErrorLog event = SystemErrorLog.builder()
                .occurredAt(LocalDateTime.now())
                .source(ErrorLogSource.FRONTEND)
                .severity(parseSeverity(request == null ? null : request.severity()))
                .environment(activeEnvironment())
                .correlationId(request == null ? null : request.correlationId())
                .username(username)
                .role(role)
                .path(request == null ? null : request.path())
                .statusCode(request == null ? null : request.statusCode())
                .errorCode(request == null ? null : request.errorCode())
                .userMessage(request == null ? null : request.userMessage())
                .technicalMessage(request == null ? null : request.technicalMessage())
                .stackExcerpt(request == null ? null : request.stackExcerpt())
                .frontendRoute(request == null ? null : request.frontendRoute())
                .browser(header(httpRequest, "User-Agent"))
                .build();
        service.record(event);
        return ApiResponse.success(null, "Frontend error recorded", "تم تسجيل الخطأ");
    }

    private static ErrorLogSeverity parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return ErrorLogSeverity.ERROR;
        }
        try {
            return ErrorLogSeverity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ErrorLogSeverity.ERROR;
        }
    }

    private String activeEnvironment() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "local" : profiles[0];
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null) {
            return null;
        }
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    private static String usernameOf(Authentication authentication) {
        return authentication == null ? "SYSTEM" : authentication.getName();
    }
}

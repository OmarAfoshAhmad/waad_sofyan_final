package com.waad.tba.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to populate SLF4J MDC (Mapped Diagnostic Context) with
 * request-specific information.
 * This enables better log correlation and traceability in production.
 * 
 * Fields added to MDC:
 * - traceId: A unique ID for the entire request lifecycle.
 * - username: The authenticated user's name (if available).
 * - method: HTTP method (GET, POST, etc.).
 * - uri: Requested resource path.
 * 
 * @version 1.0
 * @since 2026-03-13 (Observability Phase)
 */
@Component
public class LogMdcFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String CORRELATION_ID = "correlationId";
    private static final String USERNAME = "username";
    private static final String METHOD = "method";
    private static final String URI = "uri";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Generate or extract Correlation/Trace IDs
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId != null) {
            correlationId = correlationId.replaceAll("[\r\n]", "");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader("X-Trace-ID");
            if (correlationId != null) {
                correlationId = correlationId.replaceAll("[\r\n]", "");
            }
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String traceId = request.getHeader("X-Trace-ID");
        if (traceId != null) {
            // Sanitize to prevent CRLF injection
            traceId = traceId.replaceAll("[\r\n]", "");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = correlationId.substring(0, Math.min(8, correlationId.length()));
        }

        // 2. Clear and populate MDC
        MDC.put(TRACE_ID, traceId);
        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(METHOD, request.getMethod());
        MDC.put(URI, request.getRequestURI());

        // Add tracing headers to response for client-side debugging.
        response.setHeader("X-Trace-ID", traceId);
        response.setHeader("X-Correlation-ID", correlationId);

        try {
            // Proceed through the chain
            // Note: Username will be populated AFTER the authentication filters in the
            // chain.
            // However, we can also try to peek at the security context if it was already
            // populated
            // (e.g., by a previous filter in the chain if we re-enter or if configured
            // strictly).

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                MDC.put(USERNAME, auth.getName());
            }

            filterChain.doFilter(request, response);

        } finally {
            // ALWAYS clear MDC to prevent memory leaks and log pollution between threads in
            // the pool
            MDC.clear();
        }
    }
}

package com.waad.tba.modules.monitoring.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class MonitoringHttpErrorFilter extends OncePerRequestFilter {

    private final ErrorRateMonitor errorRateMonitor;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            if (status >= 500) {
                try {
                    errorRateMonitor.recordBackendError(status, request.getMethod(), request.getRequestURI());
                } catch (Exception e) {
                    log.warn("[MON-1C] Failed to record backend error event: {}", e.getMessage());
                }
            }
        }
    }
}

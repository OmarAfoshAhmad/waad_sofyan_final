package com.waad.tba.security;

import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SessionCookieSanitizerFilter
 * 
 * Intercepts incoming HTTP requests before Spring Session JDBC processing.
 * Sanitizes cookies and Cookie headers containing NUL bytes (0x00, %00) or invalid characters.
 * 
 * Order: Ordered.HIGHEST_PRECEDENCE ensures it runs before Spring Session (SessionRepositoryFilter).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SessionCookieSanitizerFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            if (hasCorruptedCookie(httpRequest)) {
                log.warn("⚠️ CORRUPTED_COOKIE_SANITIZED: Cleared corrupted session cookies for URI {}", httpRequest.getRequestURI());
                HttpServletRequest sanitizedRequest = new SanitizedCookieRequestWrapper(httpRequest);
                chain.doFilter(sanitizedRequest, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean hasCorruptedCookie(HttpServletRequest request) {
        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader != null && isCorrupted(cookieHeader)) {
            return true;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (isCorrupted(cookie.getName()) || isCorrupted(cookie.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCorrupted(String value) {
        if (value == null) return false;
        if (value.indexOf('\0') >= 0) return true;
        if (value.contains("%00") || value.contains("\\0") || value.contains("\\x00")) return true;
        
        try {
            String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
            if (decoded.indexOf('\0') >= 0) return true;
        } catch (Exception ignored) {}

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            if (b == 0) return true;
        }
        return false;
    }

    private static class SanitizedCookieRequestWrapper extends HttpServletRequestWrapper {

        private final Cookie[] sanitizedCookies;
        private final String sanitizedCookieHeader;

        public SanitizedCookieRequestWrapper(HttpServletRequest request) {
            super(request);
            
            // Clean cookie header
            String rawHeader = request.getHeader("Cookie");
            if (rawHeader != null && isCorrupted(rawHeader)) {
                this.sanitizedCookieHeader = cleanCookieHeader(rawHeader);
            } else {
                this.sanitizedCookieHeader = rawHeader;
            }

            // Clean cookie objects
            Cookie[] rawCookies = request.getCookies();
            if (rawCookies != null) {
                List<Cookie> cleanList = new ArrayList<>();
                for (Cookie c : rawCookies) {
                    if (!isCorrupted(c.getName()) && !isCorrupted(c.getValue())) {
                        cleanList.add(c);
                    }
                }
                this.sanitizedCookies = cleanList.isEmpty() ? null : cleanList.toArray(new Cookie[0]);
            } else {
                this.sanitizedCookies = null;
            }
        }

        private static String cleanCookieHeader(String header) {
            if (header == null) return null;
            String[] pairs = header.split(";");
            StringBuilder sb = new StringBuilder();
            for (String pair : pairs) {
                String trimmed = pair.trim();
                if (!isCorrupted(trimmed)) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(trimmed);
                }
            }
            return sb.toString();
        }

        @Override
        public Cookie[] getCookies() {
            return this.sanitizedCookies;
        }

        @Override
        public String getHeader(String name) {
            if ("Cookie".equalsIgnoreCase(name)) {
                return this.sanitizedCookieHeader;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Cookie".equalsIgnoreCase(name)) {
                if (this.sanitizedCookieHeader == null) {
                    return Collections.emptyEnumeration();
                }
                return Collections.enumeration(List.of(this.sanitizedCookieHeader));
            }
            return super.getHeaders(name);
        }
    }
}

package com.waad.tba.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SessionAuthenticationFilter — Static Role-Based Authorization (Phase 5)
 *
 * Loads the user's role from userType on each request (no dynamic permissions).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null &&
                SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            HttpSession session = request.getSession(false);

            if (session != null && session.getAttribute("userId") != null) {
                String username = (String) session.getAttribute("username");

                if (username != null) {
                    User user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

                    // Block deactivated users even if they have an existing session
                    if (user.getActive() == null || !user.getActive()) {
                        log.warn("Blocked request for deactivated user: {}", username);
                        session.invalidate();
                        filterChain.doFilter(request, response);
                        return;
                    }

                    String role = user.getUserType() != null ? user.getUserType() : "DATA_ENTRY";

                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + role));

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Session auth OK — User: {}, Role: {}, Path: {}",
                            username, role, request.getRequestURI());
                }
            }
        } catch (Exception ex) {
            log.error("Could not set session-based authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/login") ||
                path.startsWith("/api/v1/auth/register") ||
                path.startsWith("/api/v1/auth/forgot-password") ||
                path.startsWith("/api/v1/auth/reset-password") ||
                path.startsWith("/api/v1/auth/session/login") ||
                path.startsWith("/api/v1/pdf/settings/active") ||
                path.startsWith("/swagger") ||
                path.startsWith("/v3/api-docs");
    }
}

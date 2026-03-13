package com.waad.tba.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

/**
 * Security Configuration for the TBA-WAAD system.
 * 
 * Note: @EnableMethodSecurity is configured in MethodSecurityConfig
 * along with the SUPER_ADMIN bypass expression handler.
 * 
 * Note: PasswordEncoder is defined in PasswordEncoderConfig to break
 * circular dependency chain.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final LogMdcFilter logMdcFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SessionAuthenticationFilter sessionAuthenticationFilter; // Phase B: Session support
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder; // Injected from PasswordEncoderConfig

    @Value("${app.cors.allowed-origins:https://waadapp.ly,https://www.waadapp.ly}")
    private List<String> corsAllowedOrigins;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // PRODUCTION HARDENING: CSRF Protection via SameSite=Strict Cookies
                // ====================================================================
                // CSRF protection is DISABLED in Spring Security, but the system is
                // protected via SameSite=Strict cookie attribute (see CookieConfig.java)
                //
                // WHY SAMESITE=STRICT INSTEAD OF CSRF TOKENS:
                // 1. Browser-native protection - no custom token handling needed
                // 2. Zero frontend changes required
                // 3. SameSite=Strict prevents browsers from sending cookies on cross-site
                // requests
                // 4. Malicious site evil.com CANNOT trigger authenticated requests to this API
                //
                // HOW IT WORKS:
                // - Session cookie (JSESSIONID) has SameSite=Strict attribute
                // - Browser blocks cookie transmission on ALL cross-site requests
                // - Only same-site navigation (e.g., user clicking links within the app) sends
                // cookie
                // - Cross-site form POST from evil.com → cookie NOT sent → request fails (401
                // Unauthorized)
                //
                // PREVIOUS COMMENT (INCORRECT):
                // "Modern SPA + CORS provides equivalent protection" ❌
                // CORS does NOT prevent CSRF because browsers send cookies automatically
                // on cross-site requests regardless of CORS configuration.
                //
                // CURRENT PROTECTION (CORRECT):
                // SameSite=Strict cookies prevent cross-site cookie transmission ✅
                //
                // See: CookieConfig.java for SameSite=Strict implementation
                // See: STEP_3_CSRF_PROTECTION_COMPLETE.md for testing and validation
                .csrf(AbstractHttpConfigurer::disable)

                // CORS configuration with credentials support
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - Authentication (v1 API)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Public access to reports for preview (iframe friendly)
                        .requestMatchers("/api/reports/**").permitAll()
                        // Swagger / OpenAPI endpoints
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/actuator/**",
                                "/error")
                        .permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated())

                // Allow framing for report previews
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))

                // Session management configuration
                .sessionManagement(session -> session
                        // Phase C.1: Session Policy Review
                        // IF_REQUIRED allows Spring to create sessions when needed (session auth)
                        // while still supporting stateless requests (JWT auth)
                        // This enables dual authentication support (Session OR JWT)
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                .authenticationProvider(authenticationProvider())

                // Phase C.1: Filter Chain Order (CRITICAL for security)
                // Order matters: SessionAuthenticationFilter → JwtAuthenticationFilter →
                // UsernamePasswordAuthenticationFilter
                // 1. SessionAuthenticationFilter checks for valid HTTP session first
                // (preferred)
                // 2. If no session, JwtAuthenticationFilter checks for Bearer token (legacy
                // fallback)
                // 3. UsernamePasswordAuthenticationFilter handles form-based login (not used in
                // our API)
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(logMdcFilter, SessionAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Return 401 (not 403) for unauthenticated requests
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint()));

        return http.build();
    }

    /**
     * Custom entry point that returns 401 JSON for unauthenticated requests.
     * Fixes: Spring Security default behavior returns 403 instead of 401.
     */
    @Bean
    AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "error");
            body.put("code", "UNAUTHORIZED");
            body.put("message", "Authentication required. Please provide a valid token.");
            body.put("path", request.getServletPath());

            new ObjectMapper().writeValue(response.getOutputStream(), body);
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Employer-ID", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

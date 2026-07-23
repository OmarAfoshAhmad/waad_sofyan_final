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
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
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
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        csrfRepository.setHeaderName("X-XSRF-TOKEN");

        // Resolve the token eagerly so safe bootstrap requests (notably session/me)
        // issue the XSRF-TOKEN cookie before the first authenticated mutation.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http
                // Session mutations require a synchronizer token in X-XSRF-TOKEN.
                // Public credential/reset flows are not authenticated by a browser
                // session and are protected by their own validation/rate-limit controls.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers(
                                "/api/v1/auth/session/login",
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/token/forgot-password",
                                "/api/v1/auth/token/reset-password",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-verification"))

                // CORS configuration with credentials support
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - Authentication
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Reports are NOT public — contain sensitive claim data
                        .requestMatchers("/api/reports/**").authenticated()
                        // Docker/load-balancer health check — must stay public
                        .requestMatchers("/actuator/health").permitAll()
                        // Feature Flags — public endpoint (called before session is established)
                        .requestMatchers("/api/v1/admin/features/public").permitAll()
                        // Error page — Spring internal, must stay public
                        .requestMatchers("/error").permitAll()
                        // Actuator management endpoints — SUPER_ADMIN only (exposes metrics/env)
                        .requestMatchers("/actuator/**").hasRole("SUPER_ADMIN")
                        // Swagger / OpenAPI — SUPER_ADMIN only (exposes full API surface)
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**")
                        .hasRole("SUPER_ADMIN")
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

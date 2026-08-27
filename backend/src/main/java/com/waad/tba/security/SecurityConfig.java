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
                        // Public endpoints - Authentication.
                        // Enumerated deliberately: a wildcard over /auth/** also
                        // exposed /register, which minted an active internal-staff
                        // account for any anonymous caller. Anything under /auth
                        // that is not listed here falls through to
                        // .anyRequest().authenticated() or its own @PreAuthorize.
                        .requestMatchers(
                                "/api/v1/auth/session/login",
                                "/api/v1/auth/session/logout",
                                // Public by design: returns 200 with a null payload
                                // for first-time visitors; AuthContext calls it on
                                // every page load to ask "is there a session yet?".
                                "/api/v1/auth/session/me",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/token/forgot-password",
                                "/api/v1/auth/token/reset-password",
                                "/api/v1/auth/password-reset-config",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-verification")
                        .permitAll()
                        // Reports are NOT public — contain sensitive claim data
                        .requestMatchers("/api/reports/**").authenticated()
                        // Docker/load-balancer health check — must stay public
                        .requestMatchers("/actuator/health").permitAll()
                        // Feature Flags — public endpoint (called before session is established)
                        .requestMatchers("/api/v1/admin/features/public").permitAll()
                        // UI config (logo/font/system name) — public, called on app bootstrap before login
                        .requestMatchers("/api/v1/admin/system-settings/ui-config").permitAll()
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
                .headers(headers -> headers
                        // Report previews are embedded by the app itself.
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())

                        // The complement to keeping secrets out of the query
                        // string: the paths that remain still carry member ids,
                        // and search filters still carry identifiers. Without
                        // this, the browser attaches the full URL to requests
                        // leaving for any other origin.
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER))

                        // Stops a browser from second-guessing a declared
                        // content type -- the step that turns an uploaded file
                        // served back as JSON into executable script.
                        .contentTypeOptions(contentType -> {})

                        // Only meaningful over TLS, which is where production
                        // runs; harmless on plain HTTP in development.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))

                        // This service answers with JSON and serves no
                        // third-party assets. 'self' still covers the
                        // SUPER_ADMIN-only Swagger UI, which loads its bundle
                        // from this same origin.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; object-src 'none'; base-uri 'self'; "
                                        + "frame-ancestors 'self'"))

                        // Hardware and location APIs have no part in this
                        // application; denying them costs nothing and removes
                        // a class of prompt entirely.
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()")))

                // Session management configuration
                .sessionManagement(session -> session
                        // Phase C.1: Session Policy Review
                        // IF_REQUIRED allows Spring to create sessions when needed (session auth)
                        // while still supporting stateless requests (JWT auth)
                        // This enables dual authentication support (Session OR JWT)
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                .authenticationProvider(authenticationProvider())

                // One way in, and only one.
                //
                // A JWT filter used to sit alongside this as a "legacy fallback",
                // which meant the application's security was whichever of the two
                // paths was weaker. It also cut straight across the authorization
                // model the rest of this codebase is built on: permission changes
                // call sessionManagementService.revokeAll(), and a bearer token is
                // not a row anyone can delete -- its holder keeps working until it
                // expires. Nothing consumed it (the browser client calls only
                // /auth/session/*, stores no token and sends no Authorization
                // header), so it was carried purely for a mobile client that does
                // not exist.
                //
                // When one is built, it gets a revocable server-side credential --
                // a rotating refresh token stored in the database -- not a
                // long-lived self-contained one.
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(logMdcFilter, SessionAuthenticationFilter.class)

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

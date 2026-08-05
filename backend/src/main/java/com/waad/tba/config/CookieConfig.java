package com.waad.tba.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;

/**
 * Cookie Configuration for Session Management
 * 
 * PRODUCTION HARDENING: Phase 1 - Critical Fix C4
 * 
 * CSRF PROTECTION STRATEGY:
 * ========================
 * This system uses a synchronizer CSRF token as the primary defense and
 * SameSite=Strict as defense in depth.
 * 
 * The readable XSRF-TOKEN cookie is separate from the HttpOnly JSESSIONID.
 * Axios echoes that random token in X-XSRF-TOKEN on mutating requests, while
 * SameSite=Strict limits cross-site cookie transmission.
 * 
 * TECHNICAL DETAILS:
 * - Cookie Name: JSESSIONID (Spring default)
 * - SameSite: Strict (blocks all cross-site cookie transmission)
 * - HttpOnly: true (prevents JavaScript access, mitigates XSS)
 * - Secure: true in production (HTTPS-only transmission)
 * - Max-Age: 1800 seconds (30 minutes, matches session timeout)
 * 
 * BROWSER SUPPORT:
 * - Chrome 51+, Firefox 60+, Edge 16+, Safari 12+ (2018+)
 * - Unsupported browsers fall back to HttpOnly protection only
 * 
 * TRADE-OFFS:
 * - Breaks legitimate cross-site navigation (e.g., email link → app requires re-login)
 * - Accepted trade-off: Security > UX convenience for medical TPA system
 * 
 * ALTERNATIVES CONSIDERED:
 * - SameSite=Lax: Allows GET requests from cross-site → weaker protection
 * - SameSite alone: rejected because it is defense in depth, not a complete
 *   replacement for request tokens across all clients/deployment topologies
 * 
 * PRODUCTION CHECKLIST:
 * - [ ] Set SESSION_COOKIE_SECURE=true in production environment
 * - [ ] Verify HTTPS is enforced (redirect HTTP → HTTPS)
 * - [ ] Test cross-site form POST → expect 403 Forbidden
 * - [ ] Test same-site navigation → expect normal operation
 * 
 * @since 2026-02-10
 * @see SecurityConfig
 */
@Configuration
public class CookieConfig {

    /**
     * Configure session cookie with SameSite=Strict for CSRF protection.
     * 
     * This bean customizes the default cookie serializer to enforce:
     * - SameSite=Strict (primary CSRF defense)
     * - HttpOnly=true (XSS mitigation)
     * - Secure=true in production (HTTPS-only)
     * 
     * CONFIGURATION PRECEDENCE:
     * This programmatic configuration takes precedence over application.yml settings.
     * We use bean configuration for production-critical security settings to ensure
     * they cannot be accidentally overridden by environment variables.
     * 
     * @return DefaultCookieSerializerCustomizer with hardened security settings
     */
    private static final int DEFAULT_MAX_AGE_SECONDS = 1800; // 30 minutes, matches session timeout
    private static final int REMEMBER_ME_MAX_AGE_SECONDS = 30 * 24 * 60 * 60; // 30 days

    /**
     * Own {@link CookieSerializer} bean (instead of a
     * {@code DefaultCookieSerializerCustomizer}) so the JSESSIONID cookie's
     * Max-Age can vary per login based on the "Remember Me" checkbox --
     * see {@link RememberMeAwareCookieSerializer}. Providing a CookieSerializer
     * bean directly makes Spring Boot back off from creating its own default
     * one, so all hardening settings below must be set here explicitly.
     */
    @Bean
    public CookieSerializer cookieSerializer() {
        RememberMeAwareCookieSerializer cookieSerializer =
                new RememberMeAwareCookieSerializer(DEFAULT_MAX_AGE_SECONDS, REMEMBER_ME_MAX_AGE_SECONDS);

        // Cookie name (Spring default, keep consistent with YAML)
        cookieSerializer.setCookieName("JSESSIONID");

        // SameSite=Strict: CRITICAL CSRF PROTECTION
        // Prevents browser from sending cookie on ANY cross-site request
        // This is the core defense against CSRF attacks
        cookieSerializer.setSameSite("Strict");

        // HttpOnly=true: XSS MITIGATION
        // Prevents JavaScript from accessing cookie
        // Mitigates cookie theft via XSS vulnerabilities
        cookieSerializer.setUseHttpOnlyCookie(true);

        // Secure=true: HTTPS ENFORCEMENT (production only)
        // Cookie only sent over HTTPS connections
        // Set via environment variable: SESSION_COOKIE_SECURE=true
        // Default: false for local development (HTTP localhost)
        String secureFlag = System.getenv().getOrDefault("SESSION_COOKIE_SECURE", "false");
        cookieSerializer.setUseSecureCookie(Boolean.parseBoolean(secureFlag));

        // Cookie path: / (all application paths)
        // Cookie is sent for all requests under the application root
        cookieSerializer.setCookiePath("/");

        // Domain: Not set (defaults to current domain)
        // Cookie is only sent to exact domain that set it
        // Prevents subdomain cookie sharing (additional security)

        return cookieSerializer;
    }
}

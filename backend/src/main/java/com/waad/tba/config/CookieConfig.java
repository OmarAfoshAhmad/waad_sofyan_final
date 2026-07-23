package com.waad.tba.config;

import org.springframework.boot.autoconfigure.session.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    @Bean
    public DefaultCookieSerializerCustomizer cookieSerializerCustomizer() {
        return cookieSerializer -> {
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
            
            // Cookie max age: 30 minutes (1800 seconds)
            // Matches server.servlet.session.timeout in application.yml
            // After this time, browser deletes cookie (session expires)
            cookieSerializer.setCookieMaxAge(1800);
            
            // Cookie path: / (all application paths)
            // Cookie is sent for all requests under the application root
            cookieSerializer.setCookiePath("/");
            
            // Domain: Not set (defaults to current domain)
            // Cookie is only sent to exact domain that set it
            // Prevents subdomain cookie sharing (additional security)
        };
    }
    
    // NOTE: Spring Boot 3.x uses DefaultCookieSerializerCustomizer instead of
    // directly returning CookieSerializer bean. This is the recommended approach
    // for Spring Boot 3.x applications.
}

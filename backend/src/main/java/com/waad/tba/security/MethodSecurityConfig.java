package com.waad.tba.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Method Security Configuration — Static Role-Based Authorization (Phase 5)
 *
 * Enables @PreAuthorize annotations on controller methods.
 * No custom PermissionEvaluator needed — all checks are role-based.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Slf4j
public class MethodSecurityConfig {

    /**
     * The department head can perform every operation available to a medical
     * reviewer. Head-only decisions remain protected by MEDICAL_REVIEW_HEAD,
     * because role inheritance is intentionally one-way.
     */
    @Bean
    RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_INSURANCE_MANAGER > ROLE_MEDICAL_REVIEW_HEAD
                ROLE_MEDICAL_REVIEW_HEAD > ROLE_MEDICAL_REVIEWER
                """);
    }

    /**
     * Method annotations are the authoritative authorization layer in this
     * application, so they must use the same hierarchy as request security.
     */
    @Bean
    static DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(
            RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler =
                new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }
}

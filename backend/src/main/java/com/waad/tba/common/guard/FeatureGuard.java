package com.waad.tba.common.guard;

import com.waad.tba.common.config.FeatureFlagsConfig;
import com.waad.tba.modules.systemadmin.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * FeatureGuard: Enforces feature flags at the API level.
 *
 * Priority order:
 * 1. DB record in feature_flags table (dynamic, toggled from Settings UI)
 * 2. application.yml / env var fallback (FeatureFlagsConfig)
 *
 * Staff roles (SUPER_ADMIN, ADMIN, DATA_ENTRY) bypass provider-portal guards.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureGuard {

    // Flag keys — must match seeds in V100 migration
    public static final String FLAG_PROVIDER_PORTAL = "PROVIDER_PORTAL_ENABLED";
    public static final String FLAG_DIRECT_CLAIM_SUBMISSION = "DIRECT_CLAIM_SUBMISSION_ENABLED";
    public static final String FLAG_BATCH_CLAIMS = "BATCH_CLAIMS_ENABLED";

    private final FeatureFlagsConfig flags;
    private final FeatureFlagService featureFlagService;
    private final com.waad.tba.security.AuthorizationService authorizationService;

    /**
     * Guard access to Provider Portal.
     * ALLOWED for internal staff roles even when the flag is disabled.
     */
    public void requireProviderPortal() {
        if (isStaff())
            return;

        if (!isProviderPortalEnabled()) {
            log.warn("🚫 [FEATURE-GUARD] Blocked Provider Portal access (flag disabled).");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "بوابة الخدمة المباشرة غير مفعلة حالياً. يرجى إدخال المطالبات عبر نظام الدُّفعات (Batches).");
        }
    }

    /**
     * Guard access to direct single-claim submission.
     * ALLOWED for internal staff roles.
     */
    public void requireDirectClaimSubmission() {
        if (isStaff())
            return;

        if (!isDirectClaimSubmissionEnabled()) {
            log.warn("🚫 [FEATURE-GUARD] Blocked direct claim submission (flag disabled).");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "التقديم المباشر للمطالبات معطل. يتم قبول المطالبات عبر نظام الدُّفعات فقط.");
        }
    }

    /** DB-first check with yml fallback */
    public boolean isProviderPortalEnabled() {
        return featureFlagService.isFlagEnabled(FLAG_PROVIDER_PORTAL, flags.isProviderPortalEnabled());
    }

    /** DB-first check with yml fallback */
    public boolean isDirectClaimSubmissionEnabled() {
        return featureFlagService.isFlagEnabled(FLAG_DIRECT_CLAIM_SUBMISSION, flags.isDirectClaimSubmissionEnabled());
    }

    /** DB-first check with yml fallback */
    public boolean isBatchClaimsEnabled() {
        return featureFlagService.isFlagEnabled(FLAG_BATCH_CLAIMS, flags.isBatchClaimsEnabled());
    }

    private boolean isStaff() {
        try {
            com.waad.tba.modules.rbac.entity.User currentUser = authorizationService.getCurrentUser();
            return currentUser != null && authorizationService.isInternalStaff(currentUser);
        } catch (Exception e) {
            return false;
        }
    }
}

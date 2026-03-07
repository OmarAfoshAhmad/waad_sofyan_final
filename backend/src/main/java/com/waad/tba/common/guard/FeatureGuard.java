package com.waad.tba.common.guard;

import com.waad.tba.common.config.FeatureFlagsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * FeatureGuard (Phase 10): Enforces feature flags at the API level.
 * 
 * Throws 503 SERVICE_UNAVAILABLE if an access attempt is made to a disabled feature.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureGuard {
    
    private final FeatureFlagsConfig flags;
    private final com.waad.tba.security.AuthorizationService authorizationService;
    
    /**
     * Guard access to direct claim entry / Provider Portal functionality.
     * ALLOWED for staff roles (Admin/Data Entry) even if portal is disabled.
     */
    public void requireProviderPortal() {
        if (isStaff()) return;

        if (!flags.isProviderPortalEnabled()) {
            log.warn("🚫 [FEATURE-GUARD] Blocked attempt to access Provider Portal while DISABLED.");
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "بوابة الخدمة المباشرة غير مفعلة حالياً. يرجى إدخال المطالبات عبر نظام الدُّفعات (Batches)."
            );
        }
    }
    
    /**
     * Guard access to direct claim submission.
     * ALLOWED for staff roles.
     */
    public void requireDirectClaimSubmission() {
        if (isStaff()) return;

        if (!flags.isDirectClaimSubmissionEnabled()) {
            log.warn("🚫 [FEATURE-GUARD] Blocked attempt to submit claim directly while DISABLED.");
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "التقديم المباشر للمطالبات معطل. يتم قبول المطالبات عبر نظام الدُّفعات فقط."
            );
        }
    }

    private boolean isStaff() {
        try {
            com.waad.tba.modules.rbac.entity.User currentUser = authorizationService.getCurrentUser();
            return currentUser != null && authorizationService.isInternalStaff(currentUser);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isProviderPortalEnabled() {
        return flags.isProviderPortalEnabled();
    }
}

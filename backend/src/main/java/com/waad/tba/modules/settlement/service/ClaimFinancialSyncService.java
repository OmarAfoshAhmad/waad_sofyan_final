package com.waad.tba.modules.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * خدمة المزامنة المالية للمطالبات — نقطة الدخول الوحيدة لتحديث حسابات المرفقين
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * الغرض: تمركز منطق تحديث الحسابات المالية في مكان واحد بدل تكراره
 * في عمليات الإضافة والحذف والاستعادة.
 *
 * كيف تعمل:
 * - تُستدعى من ClaimApprovalEventListener و ClaimReversalEventListener
 * - كلا المستمعَين يشتغلان بعد COMMIT مباشرة (بدون Async)
 * - REQUIRES_NEW يفتح transaction جديدة مستقلة لكل عملية
 *
 * عمليات:
 * creditForClaim() ← عند إضافة مطالبة معتمدة أو استعادتها
 * reverseForClaim() ← عند حذف مطالبة معتمدة
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimFinancialSyncService {

    private final ProviderAccountService providerAccountService;

    /**
     * إضافة قيد دائن لحساب مقدم الخدمة عند اعتماد مطالبة أو استعادتها.
     * يعمل في transaction مستقلة بعد commit المطالبة مباشرة (synchronous).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void creditForClaim(Long claimId, Long userId) {
        log.info("💰 [SYNC] creditForClaim: claimId={}, userId={}", claimId, userId);
        try {
            providerAccountService.creditOnClaimApproval(claimId, userId);
            log.info("✅ [SYNC] Provider account credited for claim {}", claimId);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("already been credited")) {
                log.warn("⚠️ [SYNC] Claim {} already credited — skipping (idempotent)", claimId);
            } else {
                log.error("❌ [SYNC] Failed to credit provider account for claim {}: {}", claimId, e.getMessage());
                throw e;
            }
        }
    }

    /**
     * إضافة قيد مدين لحساب مقدم الخدمة عند حذف مطالبة معتمدة.
     * يعمل في transaction مستقلة بعد commit الحذف مباشرة (synchronous).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reverseForClaim(Long claimId, Long userId) {
        log.info("🔄 [SYNC] reverseForClaim: claimId={}, userId={}", claimId, userId);
        try {
            providerAccountService.debitOnClaimReversal(claimId, userId);
            log.info("✅ [SYNC] Provider account reversed for claim {}", claimId);
        } catch (Exception e) {
            log.error("🚨 [SYNC] Failed to reverse provider account for claim {}: {}", claimId, e.getMessage(), e);
            throw e;
        }
    }
}

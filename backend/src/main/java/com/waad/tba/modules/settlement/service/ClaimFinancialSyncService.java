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
 * - اعتماد المطالبة يشتغل قبل COMMIT داخل نفس المعاملة المالية
 * - MANDATORY يمنع إنشاء قيد مقدم خدمة خارج معاملة الاعتماد
 * - مسار العكس القديم ما زال مستقلاً مؤقتاً إلى أن تُبنى بوابة العكس الذرية
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
     * يعمل داخل نفس معاملة اعتماد المطالبة. إذا فشل قيد مقدم الخدمة، تفشل
     * المطالبة ودفتر السقوف معها؛ لا توجد حالة اعتماد مالي جزئية.
     */
    @Transactional(propagation = Propagation.MANDATORY)
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
     *
     * ملاحظة مهمة: الحذف تمّ بالفعل (AFTER_COMMIT). إذا فشل عكس الرصيد
     * نسجّل تحذيراً للمراجعة اليدوية دون أن نوقف الحذف.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reverseForClaim(Long claimId, Long userId) {
        log.info("🔄 [SYNC] reverseForClaim: claimId={}, userId={}", claimId, userId);
        try {
            providerAccountService.debitOnClaimReversal(claimId, userId);
            log.info("✅ [SYNC] Provider account reversed for claim {}", claimId);
        } catch (IllegalStateException e) {
            // رصيد غير كافٍ أو حالة غير متوقعة — الحذف تمّ بالفعل، لا نُوقفه
            log.warn("⚠️ [SYNC] Could not reverse provider account for claim {} (claim already deleted): {}",
                    claimId, e.getMessage());
        } catch (Exception e) {
            // أي خطأ آخر — نسجّل ونتجاهل لأن الحذف لا يجب أن يُعاد
            log.error("🚨 [SYNC] Unexpected error reversing provider account for claim {}: {}",
                    claimId, e.getMessage(), e);
        }
    }
}

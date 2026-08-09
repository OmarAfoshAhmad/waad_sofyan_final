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
 * - العكس يعمل قبل COMMIT داخل نفس معاملة تغيير حالة المطالبة
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
        providerAccountService.creditOnClaimApproval(claimId, userId);
        log.info("✅ [SYNC] Provider account credit present for claim {}", claimId);
    }

    /**
     * عكس قيد مقدم الخدمة داخل نفس معاملة عكس المطالبة. لا يُسمح بابتلاع
     * الأخطاء: فشل الحساب يُلغي عكس السقوف وتغيير الحالة معاً.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverseForClaim(Long claimId, Long userId) {
        log.info("🔄 [SYNC] reverseForClaim: claimId={}, userId={}", claimId, userId);
        providerAccountService.debitOnClaimReversal(claimId, userId);
        log.info("✅ [SYNC] Provider account reversal present for claim {}", claimId);
    }
}

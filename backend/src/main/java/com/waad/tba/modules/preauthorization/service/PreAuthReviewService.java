package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.preauthorization.dto.PreAuthLineDecisionDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.LineDecisionStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.PriceVarianceStatus;
import com.waad.tba.modules.preauthorization.entity.PreauthDecisionSnapshot;
import com.waad.tba.modules.preauthorization.entity.PreauthLineSnapshot;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationLineRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.repository.PreauthLineSnapshotRepository;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * خدمة مراجعة الموافقات المسبقة على مستوى سطر الخدمة.
 *
 * الدور: يُمكّن المراجع من:
 *   1. الموافقة/الرفض/التعديل على كل سطر خدمة بشكل مستقل.
 *   2. إنهاء المراجعة بعد معالجة جميع السطور.
 *   3. حساب الإجماليات النهائية تلقائياً.
 *
 * القواعد الصارمة:
 *   - contractPrice لا يُعدَّل أبداً (حقل مقدس للتدقيق).
 *   - approvedAmount + varianceAmount يُحسبان من قرار المراجع.
 *   - لا يمكن إنهاء المراجعة مع وجود سطور بحالة PENDING.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreAuthReviewService {

    private final PreAuthorizationRepository preAuthRepo;
    private final PreAuthorizationLineRepository lineRepo;
    private final PreAuthorizationAuditService auditService;
    private final AuthorizationService authorizationService;
    private final ReviewerProviderIsolationService reviewerIsolationService;
    private final PreAuthReservationLedgerService reservationLedgerService;
    private final PreauthLineSnapshotRepository lineSnapshotRepository;

    // ==================== قرار على مستوى سطر واحد ====================

    /**
     * يُطبّق قرار المراجع على سطر خدمة واحد.
     *
     * @param preAuthId معرّف الموافقة المسبقة
     * @param lineId    معرّف سطر الخدمة
     * @param dto       قرار المراجع (approvedAmount, decisionStatus, notes)
     * @param reviewer  اسم المراجع من JWT
     * @return السطر بعد التحديث
     */
    @Transactional
    public PreAuthorizationLine makeLineDecision(Long preAuthId, Long lineId,
                                                 PreAuthLineDecisionDto dto, String reviewer) {
        log.info("[REVIEW] Making line decision: preAuthId={}, lineId={}, status={}, reviewer={}",
                preAuthId, lineId, dto.getDecisionStatus(), reviewer);

        // ── 1. تحقق من صلاحية المراجع ─────────────────────────────────────
        PreAuthorization preAuth = getAndValidateForReview(preAuthId, reviewer);

        // ── 2. جلب السطر والتحقق من انتمائه للموافقة ────────────────────────
        PreAuthorizationLine line = lineRepo.findByIdAndPreAuthorizationId(lineId, preAuthId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "سطر الخدمة " + lineId + " غير موجود في الموافقة " + preAuthId));

        // ── 3. التحقق من إلزامية الملاحظات عند الرفض أو التعديل ─────────────
        if ((dto.getDecisionStatus() == LineDecisionStatus.REJECTED
                || dto.getDecisionStatus() == LineDecisionStatus.PARTIALLY_APPROVED)
                && (dto.getDecisionNotes() == null || dto.getDecisionNotes().isBlank())) {
            throw new BusinessRuleException(
                    "ملاحظات المراجع إلزامية عند رفض أو تعديل مبلغ سطر الخدمة");
        }

        // ── 4. حفظ القيم قبل التعديل للـ Audit ──────────────────────────────
        BigDecimal previousApprovedAmount = line.getApprovedAmount();
        LineDecisionStatus previousDecisionStatus = line.getDecisionStatus();

        // ── 4.5 المصدر الرسمي لقرار السطر ────────────────────────────────────
        // reviewDecision/approvedQuantity/explicitRejectedAmount هي الحقول
        // الوحيدة التي يقرأها المحرك المالي الحقيقي
        // (PreAuthorizationDecisionBuilder). ما دونها في هذه الدالة
        // (decisionStatus/approvedAmount/companyShare/patientShare) عرض فقط،
        // وتُعاد كتابته من نتيجة المحرك الفعلية بعد finalizeReview -- وليس
        // مصدراً مستقلاً للقرار المالي.
        applyCanonicalDecision(line, dto);

        // ── 5. تطبيق القرار (عرض فقط) ────────────────────────────────────────
        line.setDecisionStatus(dto.getDecisionStatus());
        line.setDecisionNotes(dto.getDecisionNotes());
        line.setDecisionReasonCode(dto.getDecisionReasonCode());

        if (dto.getDecisionStatus() == LineDecisionStatus.REJECTED) {
            // رفض كلي: approvedAmount = 0
            line.setApprovedAmount(BigDecimal.ZERO);
            line.setPatientShare(BigDecimal.ZERO);
            line.setCompanyShare(BigDecimal.ZERO);
            line.setPriceVarianceStatus(PriceVarianceStatus.UNLISTED);

        } else {
            // موافقة كاملة أو جزئية
            BigDecimal approved = dto.getApprovedAmount() != null
                    ? dto.getApprovedAmount()
                    : (line.getContractPrice() != null ? line.getContractPrice() : BigDecimal.ZERO);

            line.setApprovedAmount(approved);

            // حساب varianceAmount = contractPrice - approvedAmount
            if (line.getContractPrice() != null) {
                BigDecimal variance = line.getContractPrice().subtract(approved);
                line.setVarianceAmount(variance);

                // تحديد PriceVarianceStatus
                if (variance.compareTo(BigDecimal.ZERO) == 0) {
                    line.setPriceVarianceStatus(PriceVarianceStatus.MATCH_CONTRACT);
                } else if (variance.compareTo(BigDecimal.ZERO) > 0) {
                    // المبلغ المعتمد أقل من سعر العقد
                    BigDecimal variancePct = variance
                            .divide(line.getContractPrice(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    if (variancePct.compareTo(BigDecimal.valueOf(50)) >= 0) {
                        line.setPriceVarianceStatus(PriceVarianceStatus.CRITICAL_VARIANCE);
                    } else if (variancePct.compareTo(BigDecimal.valueOf(20)) >= 0) {
                        line.setPriceVarianceStatus(PriceVarianceStatus.HIGH_VARIANCE);
                    } else {
                        line.setPriceVarianceStatus(PriceVarianceStatus.BELOW_CONTRACT);
                    }
                } else {
                    // المبلغ المعتمد أعلى من سعر العقد (يجب ألا يحدث لكن نُسجّله)
                    line.setPriceVarianceStatus(PriceVarianceStatus.ABOVE_CONTRACT);
                }

                // حساب حصة المريض والشركة
                if (line.getCoveragePercentage() != null && line.getCoveragePercentage() > 0) {
                    BigDecimal coverageFraction = BigDecimal.valueOf(line.getCoveragePercentage())
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                    BigDecimal companyShare = approved.multiply(coverageFraction)
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal patientShare = approved.subtract(companyShare);
                    line.setCompanyShare(companyShare);
                    line.setPatientShare(patientShare);
                }
            }
        }

        lineRepo.save(line);

        // ── 6. تسجيل في Audit ─────────────────────────────────────────────────
        auditService.logUpdate(
                preAuthId,
                preAuth.getReferenceNumber(),
                reviewer,
                "LINE_" + lineId + "_" + previousDecisionStatus + "_amount=" + previousApprovedAmount,
                "LINE_" + lineId + "_" + dto.getDecisionStatus() + "_amount=" + line.getApprovedAmount(),
                dto.getDecisionNotes() != null ? dto.getDecisionNotes() : "قرار على السطر"
        );

        log.info("✅ [REVIEW] Line decision saved: lineId={}, status={}, approved={}",
                lineId, line.getDecisionStatus(), line.getApprovedAmount());

        return line;
    }

    /**
     * يترجم قرار الشاشة (decisionStatus + مبلغ معتمد اختياري) إلى الحقول
     * الكنسية التي يقرأها {@link PreAuthorizationDecisionBuilder}. البُعد
     * المستخدَم هنا هو المبلغ لا الكمية -- المراجع لا يُدخل كميات، فتبقى
     * approvedQuantity مرتبطة بـrequestedQuantity دائماً، والتمييز الفعلي
     * (كامل/جزئي/مرفوض) يحمله reviewDecision وexplicitRejectedAmount.
     */
    private void applyCanonicalDecision(PreAuthorizationLine line, PreAuthLineDecisionDto dto) {
        int requestedQuantity = line.getRequestedQuantity() != null ? line.getRequestedQuantity() : 1;
        BigDecimal requested = line.getRequestedAmount() != null ? line.getRequestedAmount() : BigDecimal.ZERO;

        switch (dto.getDecisionStatus()) {
            case REJECTED -> {
                line.setReviewDecision(PreAuthorizationLine.ReviewDecision.REJECT);
                line.setApprovedQuantity(0);
                line.setExplicitRejectedAmount(BigDecimal.ZERO);
                line.setRejectionReason(dto.getDecisionNotes());
            }
            case PARTIALLY_APPROVED -> {
                BigDecimal approved = dto.getApprovedAmount() != null ? dto.getApprovedAmount() : BigDecimal.ZERO;
                BigDecimal rejected = requested.subtract(approved);
                if (rejected.signum() < 0) {
                    throw new BusinessRuleException("المبلغ المعتمد للسطر يتجاوز المبلغ المطلوب");
                }
                line.setReviewDecision(PreAuthorizationLine.ReviewDecision.PARTIALLY_APPROVE);
                line.setApprovedQuantity(requestedQuantity);
                line.setExplicitRejectedAmount(rejected);
                line.setRejectionReason(dto.getDecisionNotes());
            }
            case APPROVED -> {
                line.setReviewDecision(PreAuthorizationLine.ReviewDecision.APPROVE);
                line.setApprovedQuantity(requestedQuantity);
                line.setExplicitRejectedAmount(BigDecimal.ZERO);
            }
            case INFO_REQUESTED -> {
                // Not a financial decision yet -- leave the canonical fields at
                // "unreviewed" rather than inventing one. finalizeReview's own
                // PENDING guard is what actually gates progress; this state
                // just isn't final.
            }
            default -> throw new BusinessRuleException("حالة قرار غير مدعومة: " + dto.getDecisionStatus());
        }
    }

    // ==================== إنهاء المراجعة النهائية ====================

    /**
     * يُنهي المراجعة بعد أن يُقرر المراجع على جميع السطور.
     *
     * لم تعد الإجماليات تُحسب هنا يدوياً من decisionStatus/approvedAmount --
     * تلك حقول عرض فقط الآن. القرار المالي الحقيقي (بما فيه فحص كل وعاء
     * وسقف، وحجزه فعلياً في الدفتر) يأتي حصراً من
     * {@link PreAuthReservationLedgerService#approveAndReserve}، وهو ما يجعل
     * هذا المسار و`/approve` ينتهيان في نفس المحرك. لا تتحول الحالة إلى
     * APPROVED/PARTIALLY_APPROVED إلا بعد نجاح الحجز في نفس المعاملة --
     * approveAndReserve تكتب الحالة قبل flush الدفتر، فإخفاق الحجز يُرجع
     * المعاملة كلها ولا تبقى APPROVED بلا حجز.
     *
     * @param preAuthId معرّف الموافقة المسبقة
     * @param reviewer  اسم المراجع من JWT
     */
    @Transactional
    public PreAuthorization finalizeReview(Long preAuthId, String reviewer) {
        log.info("[REVIEW] Finalizing review: preAuthId={}, reviewer={}", preAuthId, reviewer);

        PreAuthorization preAuth = getAndValidateForReview(preAuthId, reviewer);
        List<PreAuthorizationLine> lines = lineRepo.findByPreAuthorizationId(preAuthId);

        // ── 1. التأكد من عدم وجود سطور معلقة ────────────────────────────────
        long pendingCount = lines.stream()
                .filter(l -> l.getDecisionStatus() == LineDecisionStatus.PENDING)
                .count();
        if (pendingCount > 0) {
            throw new BusinessRuleException(
                    "لا يمكن إنهاء المراجعة. توجد " + pendingCount + " سطور لم يُتخذ قرار بشأنها بعد.");
        }

        boolean allRejected = lines.stream()
                .allMatch(l -> l.getDecisionStatus() == LineDecisionStatus.REJECTED);

        if (allRejected) {
            // لا شيء لحجزه -- approveAndReserve ترفض أصلاً في هذه الحالة
            // بعينها (outcome=REJECTED)، فلا داعٍ لاستدعائها.
            return rejectEntirely(preAuth, lines, reviewer);
        }

        PreauthDecisionSnapshot snapshot = reservationLedgerService.approveAndReserve(preAuthId, null, reviewer);
        resyncLinesFromSnapshot(lines, snapshot.getId());

        preAuth = preAuthRepo.findById(preAuthId).orElseThrow();

        auditService.logApprove(preAuthId, preAuth.getReferenceNumber(), reviewer,
                "إنهاء المراجعة: " + preAuth.getStatus().getArabicLabel()
                        + " | الإجمالي المعتمد: " + preAuth.getApprovedTotalAmount() + " د.ل");

        log.info("✅ [REVIEW] Review finalized: preAuthId={}, status={}, totalApproved={}",
                preAuthId, preAuth.getStatus(), preAuth.getApprovedTotalAmount());

        return preAuth;
    }

    private PreAuthorization rejectEntirely(PreAuthorization preAuth, List<PreAuthorizationLine> lines,
            String reviewer) {
        preAuth.setStatus(PreAuthStatus.REJECTED);
        preAuth.setApprovedAmount(BigDecimal.ZERO);
        preAuth.setApprovedTotalAmount(BigDecimal.ZERO);
        preAuth.setPatientShare(BigDecimal.ZERO);
        preAuth.setCompanyShare(BigDecimal.ZERO);
        preAuth.setReservedAmount(BigDecimal.ZERO);
        preAuth.setApprovedBy(reviewer);
        preAuth.setApprovedAt(LocalDateTime.now());
        preAuth.setUpdatedBy(reviewer);
        preAuthRepo.save(preAuth);

        auditService.logApprove(preAuth.getId(), preAuth.getReferenceNumber(), reviewer,
                "إنهاء المراجعة: " + PreAuthStatus.REJECTED.getArabicLabel() + " | كل السطور مرفوضة");

        log.info("✅ [REVIEW] Review finalized as fully rejected: preAuthId={}", preAuth.getId());
        return preAuth;
    }

    /**
     * الحقول القديمة (approvedAmount/companyShare/patientShare/decisionStatus)
     * تصبح عرضاً لِما قرره المحرك فعلاً، لا حساباً موازياً له -- تُقرأ من
     * PreauthLineSnapshot الذي approveAndReserve كتبه للتو، وليس من إدخال
     * المراجع الخام.
     */
    private void resyncLinesFromSnapshot(List<PreAuthorizationLine> lines, Long decisionSnapshotId) {
        Map<Long, PreauthLineSnapshot> byLineId = lineSnapshotRepository
                .findByDecisionSnapshotId(decisionSnapshotId).stream()
                .collect(java.util.stream.Collectors.toMap(PreauthLineSnapshot::getPreauthLineId, s -> s));

        for (PreAuthorizationLine line : lines) {
            PreauthLineSnapshot snap = byLineId.get(line.getId());
            if (snap == null) continue; // لا أثر مالي لهذا السطر (مثلاً رفض صريح لم يُنتج حركة)
            line.setApprovedAmount(snap.getApprovedAmount());
            line.setCompanyShare(snap.getCompanyShare());
            line.setPatientShare(snap.getPatientShare());
            line.setDecisionStatus(switch (snap.getReviewDecision()) {
                case "REJECT" -> LineDecisionStatus.REJECTED;
                case "PARTIALLY_APPROVE" -> LineDecisionStatus.PARTIALLY_APPROVED;
                default -> LineDecisionStatus.APPROVED;
            });
            lineRepo.save(line);
        }
    }

    // ==================== بدء مراجعة (PENDING → UNDER_REVIEW) ====================

    /**
     * يُحوّل وضع الموافقة من PENDING إلى UNDER_REVIEW ويُسجّل بدء المراجعة.
     */
    @Transactional
    public PreAuthorization startReview(Long preAuthId, String reviewer) {
        log.info("[REVIEW] Starting review: preAuthId={}, reviewer={}", preAuthId, reviewer);

        PreAuthorization preAuth = preAuthRepo.findById(preAuthId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "الموافقة المسبقة غير موجودة: " + preAuthId));

        if (!preAuth.getActive()) {
            throw new BusinessRuleException("الموافقة المسبقة غير نشطة");
        }

        if (preAuth.getStatus() != PreAuthStatus.PENDING
                && preAuth.getStatus() != PreAuthStatus.SUBMITTED
                && preAuth.getStatus() != PreAuthStatus.RESUBMITTED) {
            throw new BusinessRuleException(
                    "لا يمكن بدء المراجعة من وضع: " + preAuth.getStatus().getArabicLabel());
        }

        preAuth.setStatus(PreAuthStatus.UNDER_REVIEW);
        preAuth.setUpdatedBy(reviewer);
        preAuth.setReviewedAt(LocalDateTime.now());
        preAuthRepo.save(preAuth);

        auditService.logUpdate(preAuthId, preAuth.getReferenceNumber(), reviewer,
                PreAuthStatus.PENDING.name(), PreAuthStatus.UNDER_REVIEW.name(), "بدأ المراجعة");

        log.info("✅ [REVIEW] Review started: preAuthId={}", preAuthId);
        return preAuth;
    }

    // ==================== مساعد داخلي ====================

    private PreAuthorization getAndValidateForReview(Long preAuthId, String reviewer) {
        PreAuthorization preAuth = preAuthRepo.findById(preAuthId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "الموافقة المسبقة غير موجودة: " + preAuthId));

        if (!preAuth.getActive()) {
            throw new BusinessRuleException("الموافقة المسبقة غير نشطة");
        }

        // التحقق من الصلاحية
        User currentUser = authorizationService.getCurrentUser();
        if (!authorizationService.isReviewer(currentUser)
                && !authorizationService.isSuperAdmin(currentUser)) {
            throw new AccessDeniedException("فقط المراجعون يمكنهم اتخاذ قرارات على الموافقات");
        }

        // عزل المراجع: لا يرى إلا مزوديه المعيّنين
        if (authorizationService.isReviewer(currentUser)) {
            reviewerIsolationService.validateReviewerAccess(currentUser, preAuth.getProviderId());
        }

        // التحقق من أن الوضع يسمح بالمراجعة
        if (preAuth.getStatus() != PreAuthStatus.PENDING
                && preAuth.getStatus() != PreAuthStatus.UNDER_REVIEW
                && preAuth.getStatus() != PreAuthStatus.SUBMITTED
                && preAuth.getStatus() != PreAuthStatus.RESUBMITTED) {
            throw new BusinessRuleException(
                    "لا يمكن مراجعة الموافقة في وضع: " + preAuth.getStatus().getArabicLabel());
        }

        return preAuth;
    }
}

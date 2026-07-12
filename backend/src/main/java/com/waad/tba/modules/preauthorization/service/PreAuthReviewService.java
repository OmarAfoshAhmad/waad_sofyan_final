package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.preauthorization.dto.PreAuthLineDecisionDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.LineDecisionStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.PriceVarianceStatus;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationLineRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
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

        // ── 5. تطبيق القرار ───────────────────────────────────────────────────
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

    // ==================== إنهاء المراجعة النهائية ====================

    /**
     * يُنهي المراجعة بعد أن يُقرر المراجع على جميع السطور.
     * يحسب الإجماليات النهائية ويُحدّث وضع الموافقة المسبقة.
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

        // ── 2. حساب الإجماليات ────────────────────────────────────────────────
        BigDecimal totalApproved = lines.stream()
                .filter(l -> l.getDecisionStatus() != LineDecisionStatus.REJECTED)
                .map(l -> l.getApprovedAmount() != null ? l.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPatientShare = lines.stream()
                .map(l -> l.getPatientShare() != null ? l.getPatientShare() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCompanyShare = lines.stream()
                .map(l -> l.getCompanyShare() != null ? l.getCompanyShare() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── 3. تحديد وضع الموافقة النهائية ──────────────────────────────────
        boolean allApproved = lines.stream()
                .allMatch(l -> l.getDecisionStatus() == LineDecisionStatus.APPROVED);
        boolean allRejected = lines.stream()
                .allMatch(l -> l.getDecisionStatus() == LineDecisionStatus.REJECTED);

        PreAuthStatus finalStatus;
        if (allApproved) {
            finalStatus = PreAuthStatus.APPROVED;
        } else if (allRejected) {
            finalStatus = PreAuthStatus.REJECTED;
        } else {
            finalStatus = PreAuthStatus.PARTIALLY_APPROVED;
        }

        // ── 4. تحديث الموافقة المسبقة ────────────────────────────────────────
        preAuth.setStatus(finalStatus);
        preAuth.setApprovedAmount(totalApproved);
        preAuth.setApprovedTotalAmount(totalApproved);
        preAuth.setPatientShare(totalPatientShare);
        preAuth.setCompanyShare(totalCompanyShare);
        preAuth.setApprovedBy(reviewer);
        preAuth.setApprovedAt(LocalDateTime.now());
        preAuth.setUpdatedBy(reviewer);

        // حساب reservedAmount = companyShare للمتابعة (لا يُخصم من الحد)
        preAuth.setReservedAmount(totalCompanyShare);

        preAuthRepo.save(preAuth);

        // ── 5. Audit ──────────────────────────────────────────────────────────
        auditService.logApprove(preAuthId, preAuth.getReferenceNumber(), reviewer,
                "إنهاء المراجعة: " + finalStatus.getArabicLabel()
                        + " | الإجمالي المعتمد: " + totalApproved + " د.ل");

        log.info("✅ [REVIEW] Review finalized: preAuthId={}, status={}, totalApproved={}",
                preAuthId, finalStatus, totalApproved);

        return preAuth;
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
                && preAuth.getStatus() != PreAuthStatus.SUBMITTED) {
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
                && !authorizationService.isInsuranceAdmin(currentUser)
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
                && preAuth.getStatus() != PreAuthStatus.SUBMITTED) {
            throw new BusinessRuleException(
                    "لا يمكن مراجعة الموافقة في وضع: " + preAuth.getStatus().getArabicLabel());
        }

        return preAuth;
    }
}

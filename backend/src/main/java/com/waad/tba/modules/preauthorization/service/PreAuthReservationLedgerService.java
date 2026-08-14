package com.waad.tba.modules.preauthorization.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitConsumptionEntryWriter;
import com.waad.tba.modules.benefitpolicy.service.LedgerConstraintTranslator;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreauthDecisionSnapshot;
import com.waad.tba.modules.preauthorization.entity.PreauthLineLimitSnapshot;
import com.waad.tba.modules.preauthorization.entity.PreauthLineSnapshot;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.repository.PreauthDecisionSnapshotRepository;
import com.waad.tba.modules.preauthorization.repository.PreauthLineLimitSnapshotRepository;
import com.waad.tba.modules.preauthorization.repository.PreauthLineSnapshotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The pre-authorization side of the consumption ledger: the only component
 * that may create a hold, and the only one that may give it back.
 *
 * The three operations exist together by design. A hold removes limit from a
 * member before any claim exists; if nothing could release it, that limit
 * would be gone permanently with no claim to explain where it went. So the
 * approval path and both exits ship as one unit, and the structural guard
 * refuses a reservation writer that arrives without them.
 *
 * Every operation is one transaction. A snapshot without its holds would
 * promise money the ledger never reserved; holds without their snapshot would
 * remove limit with nothing recording why. Neither half is meaningful alone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreAuthReservationLedgerService {

    private final PreAuthorizationRepository preauthRepository;
    private final MemberRepository memberRepository;
    private final BenefitPolicyRepository policyRepository;
    private final BenefitLimitBucketRepository bucketRepository;
    private final BenefitBucketConsumptionRepository consumptionRepository;
    private final BenefitConsumptionEntryWriter entryWriter;
    private final PreAuthorizationDecisionBuilder decisionBuilder;
    private final PreauthDecisionSnapshotRepository decisionSnapshotRepository;
    private final PreauthLineSnapshotRepository lineSnapshotRepository;
    private final PreauthLineLimitSnapshotRepository limitSnapshotRepository;
    private final LedgerConstraintTranslator constraintTranslator;

    /**
     * How long an approval stays usable. Required and positive: an approval
     * with no expiry holds a member's limit forever, and guessing a number
     * inside the service would bury a commercial decision in code.
     */
    @Value("${waad.preauth.validity-days:0}")
    private int configuredValidityDays;

    /** Statuses an approval may be granted from. */
    private static final List<PreAuthorization.PreAuthStatus> APPROVABLE = List.of(
            PreAuthorization.PreAuthStatus.SUBMITTED,
            PreAuthorization.PreAuthStatus.PENDING,
            PreAuthorization.PreAuthStatus.UNDER_REVIEW,
            PreAuthorization.PreAuthStatus.RESUBMITTED,
            PreAuthorization.PreAuthStatus.APPROVAL_IN_PROGRESS);

    /** Statuses a hold may still be released from. */
    private static final List<PreAuthorization.PreAuthStatus> RELEASABLE = List.of(
            PreAuthorization.PreAuthStatus.APPROVED,
            PreAuthorization.PreAuthStatus.PARTIALLY_APPROVED,
            PreAuthorization.PreAuthStatus.ACKNOWLEDGED);

    // ── approval ─────────────────────────────────────────────────────────

    @Transactional
    public PreauthDecisionSnapshot approveAndReserve(Long preauthId, Long expectedVersion, String decidedBy) {
        String idempotencyKey = "PREAUTH_APPROVE:" + preauthId + ":" + 1;

        // Re-running the same approval returns the original decision rather
        // than placing a second set of holds against the same member.
        Optional<PreauthDecisionSnapshot> existing =
                decisionSnapshotRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        PreAuthorization preauth = lockForWrite(preauthId);
        requireVersion(preauth, expectedVersion);
        if (!APPROVABLE.contains(preauth.getStatus())) {
            throw new BusinessRuleException(
                    "لا يمكن اعتماد موافقة في الحالة الحالية: " + preauth.getStatus().getArabicLabel());
        }
        if (preauth.getExpectedServiceDate() == null) {
            throw new BusinessRuleException("لا يمكن اعتماد موافقة بدون تاريخ الخدمة المتوقع.");
        }
        memberRepository.findByIdWithLock(preauth.getMemberId())
                .orElseThrow(() -> new BusinessRuleException("المستفيد المرتبط بالموافقة غير موجود."));

        // Everything computed before the bucket locks is a PREVIEW. Between
        // reading a balance and locking it, another approval for the same
        // member can take the same limit -- so the figures that decide are the
        // ones read after the locks below.
        PreAuthorizationDecision preview = decisionBuilder.build(preauthId, 1);
        lockBucketsInAscendingOrder(preview);

        // Re-run under the locks. This is the decision that counts.
        PreAuthorizationDecision decision = decisionBuilder.build(preauthId, 1);

        if (decision.outcome() == PreAuthorizationDecision.Outcome.REJECTED) {
            // A refusal grants nothing, so there is nothing to snapshot and
            // nothing to hold.
            throw new BusinessRuleException(Optional.ofNullable(decision.rejectionReason())
                    .orElse("رُفضت الموافقة المسبقة."));
        }

        PreauthDecisionSnapshot snapshot = persistSnapshot(preauth, decision, decidedBy, idempotencyKey);
        applyApprovalToPreAuthorization(preauth, decision);

        try {
            entryWriter.flush();
        } catch (DataAccessException | jakarta.persistence.PersistenceException failure) {
            throw translated(failure);
        }
        return snapshot;
    }

    // ── the two exits ────────────────────────────────────────────────────

    @Transactional
    public int cancelAndRelease(Long preauthId, String reason, String actor) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("إلغاء الموافقة يتطلب سبباً صريحاً.");
        }
        return release(preauthId,
                BenefitBucketConsumption.ReversalReason.PREAUTH_CANCELLATION,
                PreAuthorization.PreAuthStatus.CANCELLED,
                "PREAUTH_CANCEL", reason, actor, null);
    }

    /**
     * Takes an eventId so a scheduled sweep can retry safely: the same expiry
     * event releases once, however many times the job fires.
     */
    @Transactional
    public int expireAndRelease(Long preauthId, String eventId) {
        return release(preauthId,
                BenefitBucketConsumption.ReversalReason.PREAUTH_EXPIRY,
                PreAuthorization.PreAuthStatus.EXPIRED,
                "PREAUTH_EXPIRE", "انتهت صلاحية الموافقة المسبقة", "SYSTEM", eventId);
    }

    private int release(Long preauthId, BenefitBucketConsumption.ReversalReason reason,
            PreAuthorization.PreAuthStatus finalStatus, String keyPrefix, String note,
            String actor, String eventId) {

        PreAuthorization preauth = lockForWrite(preauthId);

        // Already gone: return the original outcome rather than releasing
        // twice or overwriting the first reason.
        if (preauth.getStatus() == finalStatus) {
            return 0;
        }
        if (preauth.getStatus() == PreAuthorization.PreAuthStatus.CONSUMED
                || preauth.getStatus() == PreAuthorization.PreAuthStatus.USED) {
            throw new BusinessRuleException(
                    "لا يمكن إلغاء أو إنهاء موافقة تحولت إلى مطالبة.");
        }
        if (!RELEASABLE.contains(preauth.getStatus())) {
            throw new BusinessRuleException(
                    "لا توجد حجوزات قابلة للتحرير في الحالة الحالية: "
                            + preauth.getStatus().getArabicLabel());
        }
        if (finalStatus == PreAuthorization.PreAuthStatus.EXPIRED
                && preauth.getExpiryDate() != null
                && LocalDate.now().isBefore(preauth.getExpiryDate())) {
            throw new BusinessRuleException("لا يمكن إنهاء موافقة قبل انتهاء صلاحيتها.");
        }

        memberRepository.findByIdWithLock(preauth.getMemberId());

        List<BenefitBucketConsumption> originals =
                consumptionRepository.findActiveReservationsForPreauth(preauthId);
        originals.sort(Comparator.comparing(c -> c.getBucket() == null ? 0L : c.getBucket().getId()));

        int released = 0;
        for (BenefitBucketConsumption original : originals) {
            if (original.getBucket() != null) {
                bucketRepository.findByIdForUpdate(original.getBucket().getId());
            }

            // Release only what is still OUTSTANDING. Releasing the original
            // amount blindly would give back more than is held whenever part
            // of it was already returned.
            BigDecimal outstandingAmount = outstandingAmount(original);
            int outstandingTimes = outstandingTimes(original);
            if (outstandingAmount.signum() == 0 && outstandingTimes == 0) {
                continue;
            }

            String key = keyPrefix + ":" + preauthId + ":" + original.getId()
                    + (eventId == null ? "" : ":" + eventId);
            if (consumptionRepository.existsByIdempotencyKey(key)) {
                continue;
            }

            entryWriter.appendPreAuthRelease(original, outstandingAmount, outstandingTimes,
                    reason, key, LocalDateTime.now());
            released++;
        }

        preauth.setStatus(finalStatus);
        preauth.setDecisionNotes(note);
        preauth.setDecisionBy(actor);
        preauth.setDecisionAt(LocalDateTime.now());
        preauthRepository.save(preauth);

        try {
            entryWriter.flush();
        } catch (DataAccessException | jakarta.persistence.PersistenceException failure) {
            throw translated(failure);
        }
        return released;
    }

    // ── persistence of the decision ──────────────────────────────────────

    private PreauthDecisionSnapshot persistSnapshot(PreAuthorization preauth,
            PreAuthorizationDecision decision, String decidedBy, String idempotencyKey) {

        var basis = decision.basis();
        PreauthDecisionSnapshot head = decisionSnapshotRepository.save(PreauthDecisionSnapshot.builder()
                .preauthId(decision.preauthId())
                .calculationVersion(decision.calculationVersion())
                .memberId(decision.memberId())
                .memberPolicyAssignmentId(basis.memberPolicyAssignmentId())
                .policyId(basis.policyId())
                .expectedServiceDate(basis.expectedServiceDate())
                .providerId(basis.providerId())
                .providerContractId(basis.providerContractId())
                .contractTermsId(basis.contractTermsId())
                .discountPercent(basis.discountPercent())
                .discountBeforeRejection(basis.discountBeforeRejection())
                .requestedTotal(decision.requestedTotal())
                .settlementTotal(decision.settlementTotal())
                .authorizedServiceTotal(decision.authorizedServiceTotal())
                .providerDiscountTotal(decision.providerDiscountTotal())
                .limitExcessTotal(decision.limitExcessTotal())
                .limitCapped(decision.limitCapped())
                .rejectedTotal(decision.rejectedTotal())
                .patientShareTotal(decision.patientShareTotal())
                .companyShareTotal(decision.companyShareTotal())
                .decisionStatus(decision.outcome().name())
                .coverageOutcome(decision.coverageOutcome().name())
                .decidedBy(decidedBy)
                .idempotencyKey(idempotencyKey)
                .build());

        BenefitPolicy policy = policyRepository.findById(basis.policyId()).orElseThrow();

        for (PreAuthorizationDecision.Line line : decision.lines()) {
            PreauthLineSnapshot lineSnapshot = lineSnapshotRepository.save(PreauthLineSnapshot.builder()
                    .decisionSnapshotId(head.getId())
                    .preauthLineId(line.preauthLineId())
                    .providerServiceId(line.providerServiceId())
                    .medicalServiceId(line.medicalServiceId())
                    .medicalCategoryId(line.medicalCategoryId())
                    .benefitRuleId(line.benefitRuleId())
                    .serviceCode(line.serviceCode())
                    .serviceName(line.serviceName())
                    .quantity(Math.max(1, line.approvedQuantity()))
                    .requestedQuantity(line.requestedQuantity())
                    .approvedQuantity(line.approvedQuantity())
                    .reviewDecision(line.reviewDecision())
                    .rejectionReason(line.rejectionReason())
                    .unitPrice(line.unitPrice())
                    .requestedAmount(line.requestedAmount())
                    .coveragePercent(line.coveragePercent())
                    .copayAmount(line.copayAmount())
                    .rejectedAmount(line.rejectedAmount())
                    .approvedAmount(line.authorizedServiceAmount())
                    .patientShare(line.patientShare())
                    .companyShare(line.companyShare())
                    .build());

            for (PreAuthorizationDecision.LimitHold hold : line.limitHolds()) {
                // The snapshot is written even when nothing is held: it is
                // what explains an exhausted ceiling. The LEDGER is not.
                limitSnapshotRepository.save(PreauthLineLimitSnapshot.builder()
                        .lineSnapshotId(lineSnapshot.getId())
                        .limitScope(hold.limitScope())
                        .limitSemanticKey(hold.limitSemanticKey())
                        .bucketId(hold.bucketId())
                        .policyId(hold.policyId())
                        .periodType(hold.periodType())
                        .periodStart(hold.periodStart())
                        .periodEnd(hold.periodEnd())
                        .effectiveLimit(hold.effectiveLimit())
                        .committedBefore(hold.committedBefore())
                        .reservedBefore(hold.reservedBefore())
                        .actualRemainingBefore(hold.actualRemainingBefore())
                        .reservableAvailableBefore(hold.reservableAvailableBefore())
                        .timesLimit(hold.timesLimit())
                        .committedTimesBefore(hold.committedTimesBefore())
                        .reservedTimesBefore(hold.reservedTimesBefore())
                        .actualRemainingTimesBefore(hold.actualRemainingTimesBefore())
                        .reservableTimesBefore(hold.reservableTimesBefore())
                        .consumptionBasis(hold.consumptionBasis())
                        .reservedUnit(hold.reservedUnit().name())
                        .amountReserved(hold.amountReserved())
                        .timesReserved(hold.timesReserved())
                        .daysReserved(0)
                        .binding(hold.binding())
                        .build());

                appendHoldIfAnyMovement(preauth, policy, decision, lineSnapshot, hold);
            }
        }
        return head;
    }

    private void appendHoldIfAnyMovement(PreAuthorization preauth, BenefitPolicy policy,
            PreAuthorizationDecision decision, PreauthLineSnapshot lineSnapshot,
            PreAuthorizationDecision.LimitHold hold) {

        BigDecimal amount = Optional.ofNullable(hold.amountReserved()).orElse(BigDecimal.ZERO);
        int times = Optional.ofNullable(hold.timesReserved()).orElse(0);

        // An exhausted ceiling holds nothing. The snapshot above records the
        // decision; a ledger movement of zero in both dimensions would record
        // an effect that did not happen.
        if (amount.signum() == 0 && times == 0) {
            return;
        }

        BenefitLimitBucket bucket = hold.bucketId() == null ? null
                : bucketRepository.findById(hold.bucketId()).orElseThrow();

        String key = "PREAUTH_RESERVE:" + lineSnapshot.getId() + ":" + hold.limitScope() + ":"
                + (hold.bucketId() == null ? "GENERAL" : hold.bucketId()) + ":"
                + hold.periodStart() + ":" + hold.periodEnd();
        if (consumptionRepository.existsByIdempotencyKey(key)) {
            return;
        }

        entryWriter.appendPreAuthReservation(
                decision.preauthId(), lineSnapshot.getPreauthLineId(), policy, decision.memberId(),
                bucket,
                "POLICY_GENERAL".equals(hold.limitScope())
                        ? BenefitBucketConsumption.LimitScope.POLICY_GENERAL
                        : BenefitBucketConsumption.LimitScope.BUCKET,
                hold.periodStart(), hold.periodEnd(), amount, times,
                decision.calculationVersion(), key);
    }

    private void applyApprovalToPreAuthorization(PreAuthorization preauth,
            PreAuthorizationDecision decision) {

        if (configuredValidityDays <= 0) {
            // An approval with no expiry holds limit forever. Refusing is the
            // only safe answer to a missing commercial setting.
            throw new BusinessRuleException(
                    "مدة صلاحية الموافقة المسبقة غير مضبوطة (waad.preauth.validity-days). "
                            + "لا يمكن اعتماد موافقة بلا انتهاء.");
        }

        LocalDateTime approvedAt = LocalDateTime.now();
        preauth.setStatus(decision.outcome() == PreAuthorizationDecision.Outcome.PARTIALLY_APPROVED
                ? PreAuthorization.PreAuthStatus.PARTIALLY_APPROVED
                : PreAuthorization.PreAuthStatus.APPROVED);
        preauth.setApprovedTotalAmount(decision.companyShareTotal());
        preauth.setApprovedAt(approvedAt);
        preauth.setExpiryDate(approvedAt.toLocalDate().plusDays(configuredValidityDays));
        preauthRepository.save(preauth);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private PreAuthorization lockForWrite(Long preauthId) {
        // A real row lock, not optimistic versioning alone: two approvals
        // reading the same balance must not both proceed to hold it.
        return preauthRepository.findByIdForUpdate(preauthId)
                .orElseThrow(() -> new BusinessRuleException("الموافقة المسبقة غير موجودة."));
    }

    private void requireVersion(PreAuthorization preauth, Long expectedVersion) {
        if (expectedVersion != null && !Objects.equals(preauth.getVersion(), expectedVersion)) {
            throw new BusinessRuleException(
                    "تم تعديل الموافقة المسبقة من مستخدم آخر. أعد تحميلها ثم حاول مجدداً.");
        }
    }

    /**
     * Buckets are locked in ascending id order so two approvals touching the
     * same pair can never take them in opposite orders and deadlock.
     */
    private void lockBucketsInAscendingOrder(PreAuthorizationDecision decision) {
        List<Long> bucketIds = new ArrayList<>(decision.lines().stream()
                .flatMap(line -> line.limitHolds().stream())
                .map(PreAuthorizationDecision.LimitHold::bucketId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList());
        for (Long bucketId : bucketIds) {
            bucketRepository.findByIdForUpdate(bucketId);
        }
    }

    private BigDecimal outstandingAmount(BenefitBucketConsumption original) {
        BigDecimal released = Optional.ofNullable(
                consumptionRepository.sumReleasedAmount(original.getId())).orElse(BigDecimal.ZERO);
        return Optional.ofNullable(original.getApprovedAmount()).orElse(BigDecimal.ZERO)
                .subtract(released).max(BigDecimal.ZERO);
    }

    private int outstandingTimes(BenefitBucketConsumption original) {
        int released = Optional.ofNullable(
                consumptionRepository.sumReleasedTimes(original.getId())).orElse(0);
        return Math.max(0, Optional.ofNullable(original.getTimesConsumed()).orElse(0) - released);
    }

    private BusinessRuleException translated(RuntimeException failure) {
        String explanation = constraintTranslator.translate(failure);
        log.error("Pre-authorization ledger write refused", failure);
        return new BusinessRuleException(
                explanation != null ? explanation
                        : "تعذر تنفيذ العملية على دفتر الاستهلاك.", failure);
    }
}

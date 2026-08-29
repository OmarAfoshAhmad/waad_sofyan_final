package com.waad.tba.modules.member.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.benefitpolicy.service.GeneralCeilingReading;
import com.waad.tba.modules.member.dto.MemberLimitUpliftDto;
import com.waad.tba.modules.member.dto.MemberLimitUpliftRequest;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberGeneralLimitUplift;
import com.waad.tba.modules.member.repository.MemberGeneralLimitUpliftRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.MemberCommandAccessPolicy;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.modules.systemadmin.service.AuditLogService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Grants, revokes and resolves exceptional increases to a member's general
 * ceiling.
 *
 * The exception is additive and dated, and this class is the only thing that
 * writes one. LimitBalanceReader consumes the resolved figure and knows
 * nothing about how it came to be, which is what keeps dated resolution
 * unable to see the reason and this service unable to compute a balance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberLimitUpliftService {

    private final MemberGeneralLimitUpliftRepository upliftRepository;
    private final MemberRepository memberRepository;
    private final MemberCommandAccessPolicy commandAccessPolicy;
    private final BenefitPolicyCoverageService coverageService;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final MemberImportMetrics metrics;

    // ── resolution ─────────────────────────────────────────────────────────

    /**
     * The uplift in force for each of these members on this date.
     *
     * Deliberately not authorized: it is a component of a ceiling read, and
     * the caller has already answered who may see that ceiling. Adding a
     * second check here would let the two disagree.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> inForceFor(Collection<Long> memberIds, LocalDate asOfDate) {
        Map<Long, BigDecimal> byMember = new HashMap<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return byMember;
        }
        for (Object[] row : upliftRepository.sumInForceByMember(memberIds, asOfDate)) {
            byMember.put((Long) row[0], (BigDecimal) row[1]);
        }
        return byMember;
    }

    @Transactional(readOnly = true)
    public BigDecimal inForceFor(Long memberId, LocalDate asOfDate) {
        BigDecimal total = upliftRepository.sumInForceFor(memberId, asOfDate);
        return total == null ? BigDecimal.ZERO : total;
    }

    // ── reading the history ────────────────────────────────────────────────

    /** Everything ever granted to this member, expired and revoked included. */
    @Transactional(readOnly = true)
    public List<MemberLimitUpliftDto> historyFor(Long memberId, LocalDate asOfDate) {
        Member member = requireMember(memberId);
        commandAccessPolicy.require(MemberOperation.MANAGE_LIMIT_UPLIFT, employerIdOf(member));
        return upliftRepository.findByMemberIdOrderByEffectiveFromDescIdDesc(memberId).stream()
                .map(uplift -> MemberLimitUpliftDto.from(uplift, asOfDate))
                .toList();
    }

    // ── writing ────────────────────────────────────────────────────────────

    @Transactional
    public MemberLimitUpliftDto grant(Long memberId, MemberLimitUpliftRequest request) {
        Member member = requireMember(memberId);
        commandAccessPolicy.require(MemberOperation.MANAGE_LIMIT_UPLIFT, employerIdOf(member));

        LocalDate effectiveFrom = request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom();
        validate(request, member, effectiveFrom);

        User actor = authorizationService.getCurrentUser();
        MemberGeneralLimitUplift uplift = upliftRepository.save(MemberGeneralLimitUplift.builder()
                .memberId(memberId)
                .amount(request.amount())
                .effectiveFrom(effectiveFrom)
                .effectiveTo(request.effectiveTo())
                .source(request.source())
                .requestedByEmployerId(request.source() == MemberGeneralLimitUplift.Source.EMPLOYER_REQUEST
                        ? employerIdOf(member)
                        : null)
                .reason(request.reason().trim())
                .grantedByUserId(actor == null ? null : actor.getId())
                .grantedByUsername(actor == null ? "system" : actor.getUsername())
                .createdAt(LocalDateTime.now())
                .build());

        auditLogService.createAuditLog(
                "MEMBER_LIMIT_UPLIFT_GRANTED", "MEMBER", memberId,
                String.format("رفع السقف العام بمقدار %s اعتباراً من %s، المصدر %s، السبب: %s",
                        request.amount(), effectiveFrom, request.source(), uplift.getReason()),
                actor == null ? null : actor.getId(),
                actor == null ? null : actor.getUsername(), null, null);

        return MemberLimitUpliftDto.from(uplift, LocalDate.now());
    }

    @Transactional
    public MemberLimitUpliftDto revoke(Long upliftId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("سبب إلغاء الاستثناء إلزامي");
        }
        // Locked, not merely loaded: see findByIdForRevocation. Everything
        // below is a check followed by a write on the same row.
        MemberGeneralLimitUplift uplift = upliftRepository.findByIdForRevocation(upliftId)
                .orElseThrow(() -> new ResourceNotFoundException("استثناء السقف غير موجود"));

        Member member = requireMember(uplift.getMemberId());
        commandAccessPolicy.require(MemberOperation.MANAGE_LIMIT_UPLIFT, employerIdOf(member));

        if (uplift.getRevokedAt() != null) {
            throw new BusinessRuleException("تم إلغاء هذا الاستثناء مسبقاً");
        }
        LocalDate today = LocalDate.now();
        if (uplift.getEffectiveTo() != null && !uplift.getEffectiveTo().isAfter(today)) {
            throw new BusinessRuleException("انتهت مدة هذا الاستثناء بالفعل");
        }

        User actor = authorizationService.getCurrentUser();
        uplift.revoke(today, reason.trim(),
                actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername());
        upliftRepository.save(uplift);

        auditLogService.createAuditLog(
                "MEMBER_LIMIT_UPLIFT_REVOKED", "MEMBER", uplift.getMemberId(),
                String.format("إلغاء استثناء السقف رقم %d اعتباراً من %s، السبب: %s",
                        upliftId, uplift.getEffectiveTo(), uplift.getRevokedReason()),
                actor == null ? null : actor.getId(),
                actor == null ? null : actor.getUsername(), null, null);

        return MemberLimitUpliftDto.from(uplift, today);
    }

    // ── rules ──────────────────────────────────────────────────────────────

    private void validate(MemberLimitUpliftRequest request, Member member, LocalDate effectiveFrom) {
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new BusinessRuleException("مبلغ الاستثناء يجب أن يكون أكبر من صفر");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessRuleException("سبب رفع السقف إلزامي");
        }
        if (request.source() == null) {
            throw new BusinessRuleException("مصدر الاستثناء إلزامي");
        }
        // A ceiling is read as of a date. Backdating an uplift would change
        // what a past decision should have been without changing what it was,
        // leaving the ledger and the ceiling telling different stories about
        // the same day.
        if (effectiveFrom.isBefore(LocalDate.now())) {
            throw new BusinessRuleException("لا يمكن أن يبدأ الاستثناء بتاريخ ماضٍ");
        }
        if (request.effectiveTo() != null && !request.effectiveTo().isAfter(effectiveFrom)) {
            throw new BusinessRuleException("تاريخ نهاية الاستثناء يجب أن يكون بعد تاريخ بدايته");
        }

        // Raising a ceiling that does not exist would invent one. A member on
        // a policy with no monetary ceiling is not limited; adding to nothing
        // would produce a limit where the policy deliberately set none.
        GeneralCeilingReading ceiling = coverageService.readGeneralCeiling(member, effectiveFrom);
        if (ceiling.mode() != GeneralCeilingReading.Mode.FOUND) {
            throw new BusinessRuleException(switch (ceiling.mode()) {
                case UNLIMITED -> "وثيقة المستفيد بلا سقف عام، فلا يوجد سقف لرفعه";
                case NOT_CONFIGURED -> "لا توجد وثيقة سارية للمستفيد في هذا التاريخ";
                default -> "تعذّرت قراءة سقف المستفيد. حاول مرة أخرى";
            });
        }
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("المستفيد غير موجود"));
    }

    private Long employerIdOf(Member member) {
        return member.getEmployer() == null ? null : member.getEmployer().getId();
    }
}

package com.waad.tba.modules.preauthorization.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;

import lombok.RequiredArgsConstructor;

/**
 * The single place that takes locks for the pre-authorization life cycle.
 *
 * The order is global, not local to any one operation:
 *
 *     Member  ->  PreAuthorization  ->  Buckets (ascending id)
 *
 * The claim path already establishes it (AtomicFinancialService locks the
 * member before any bucket), and two paths taking the same locks in different
 * orders is precisely how two individually-correct paths deadlock each other.
 * Which order is nicer does not matter; that there is exactly ONE does.
 *
 * Centralising it here rather than documenting it in a comment is what makes
 * the rule survive: a comment cannot stop the next operation from locking in
 * its own order, and a structural test over line positions inside a service
 * would be checking the wrong thing.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PreAuthLockCoordinator {

    private final MemberRepository memberRepository;
    private final PreAuthorizationRepository preauthRepository;
    private final BenefitLimitBucketRepository bucketRepository;

    /**
     * Locks the member, then the pre-authorization, and confirms they still
     * belong together.
     *
     * The member id has to be read from an UNLOCKED pre-authorization first --
     * there is no other way to know whose member to lock. That read is a
     * compass, not a fact: by the time both locks are held the row may have
     * changed. So the relationship is re-checked afterwards, and a mismatch
     * stops the operation rather than continuing on locks that protect the
     * wrong member.
     *
     * It deliberately does NOT then lock the newly-named member: acquiring a
     * second member lock while holding the first is exactly the shape that
     * reintroduces the deadlock this class exists to prevent.
     */
    public PreAuthorization lockMemberThenPreAuth(Long preauthId) {
        Long compassMemberId = preauthRepository.findById(preauthId)
                .orElseThrow(() -> new BusinessRuleException("الموافقة المسبقة غير موجودة."))
                .getMemberId();
        if (compassMemberId == null) {
            throw new BusinessRuleException("الموافقة المسبقة غير مرتبطة بمستفيد.");
        }

        memberRepository.findByIdWithLock(compassMemberId)
                .orElseThrow(() -> new BusinessRuleException("المستفيد المرتبط بالموافقة غير موجود."));

        PreAuthorization locked = preauthRepository.findByIdForUpdate(preauthId)
                .orElseThrow(() -> new BusinessRuleException("الموافقة المسبقة غير موجودة."));

        if (!Objects.equals(compassMemberId, locked.getMemberId())) {
            throw new BusinessRuleException(
                    "تغيّر ارتباط الموافقة المسبقة بالمستفيد أثناء العملية. أعد المحاولة.");
        }
        return locked;
    }

    /**
     * Locks buckets in ascending id order, without duplicates, so two
     * operations touching the same pair can never take them in opposite
     * orders.
     */
    public void lockBucketsAscending(List<Long> bucketIds) {
        bucketIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(bucketRepository::findByIdForUpdate);
    }
}

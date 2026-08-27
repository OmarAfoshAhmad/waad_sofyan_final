package com.waad.tba.modules.eligibility.service;

import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest;
import com.waad.tba.modules.eligibility.dto.FamilyEligibilityResponse;
import com.waad.tba.modules.eligibility.dto.FamilyEligibilityResponse.FamilyMemberEligibility;
import com.waad.tba.modules.eligibility.dto.FamilyEligibilityResponse.FamilySummary;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Family Eligibility Service
 * Checks eligibility for a member and all family members
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyEligibilityService {

    private final EligibilityEngineService eligibilityService;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public FamilyEligibilityResponse checkFamilyEligibility(Long memberId, LocalDate serviceDate) {
        String requestId = UUID.randomUUID().toString();
        log.info("[FamilyEligibility] Checking family eligibility for member {} on {}", memberId, serviceDate);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        FamilyGroup family = resolveFamily(member);
        Map<Long, EligibilityResult> resultsByMemberId = evaluateFamily(family.principal(), family.dependents(), serviceDate);

        List<FamilyMemberEligibility> familyEligibilities = new ArrayList<>();
        FamilyMemberEligibility primaryMemberEligibility = null;
        int eligibleCount = 0;
        int ineligibleCount = 0;

        for (Member familyMember : family.all()) {
            FamilyMemberEligibility eligibility = toFamilyMemberEligibility(
                    familyMember, resultsByMemberId.get(familyMember.getId()));
            familyEligibilities.add(eligibility);

            if (familyMember.getId().equals(memberId)) {
                primaryMemberEligibility = eligibility;
            }

            if (eligibility.isEligible()) {
                eligibleCount++;
            } else {
                ineligibleCount++;
            }
        }

        String familyStatus;
        if (eligibleCount == family.all().size()) {
            familyStatus = "ALL_ELIGIBLE";
        } else if (eligibleCount > 0) {
            familyStatus = "PARTIAL";
        } else {
            familyStatus = "NONE_ELIGIBLE";
        }

        FamilySummary summary = FamilySummary.builder()
                .totalMembers(family.all().size())
                .eligibleCount(eligibleCount)
                .ineligibleCount(ineligibleCount)
                .familyStatus(familyStatus)
                .build();

        // Remove the primary member from familyMembers list (to avoid duplication)
        familyEligibilities.removeIf(e -> e.getMemberId().equals(memberId));

        return FamilyEligibilityResponse.builder()
                .requestId(requestId)
                .primaryMember(primaryMemberEligibility)
                .familyMembers(familyEligibilities)
                .summary(summary)
                .build();
    }

    /**
     * Resolves the principal + dependents for whichever family member was
     * looked up (by id or by the principal's barcode -- see
     * UnifiedMemberService.checkFamilyEligibility). A dependent's own id
     * resolves to their principal's family, same as checkFamilyEligibility
     * always did.
     */
    public FamilyGroup resolveFamily(Member anyFamilyMember) {
        Member principal = anyFamilyMember.getParent() == null ? anyFamilyMember : anyFamilyMember.getParent();
        List<Member> dependents = principal.getDependents() != null
                ? new ArrayList<>(principal.getDependents())
                : new ArrayList<>();
        return new FamilyGroup(principal, dependents);
    }

    public record FamilyGroup(Member principal, List<Member> dependents) {
        public List<Member> all() {
            List<Member> all = new ArrayList<>();
            all.add(principal);
            all.addAll(dependents);
            return all;
        }
    }

    /**
     * Single source of truth for "is this family member eligible on this
     * date" -- the actual engine call, shared by checkFamilyEligibility
     * above (memberId + serviceDate, used by the eligibility module's own
     * endpoint) and UnifiedMemberService.checkFamilyEligibility (barcode,
     * used by the member module's provider-facing endpoint). Both used to
     * run their own independent copy of this loop; now there is one.
     * Fails closed per member: an engine error marks that one member
     * not-eligible with a SYSTEM_ERROR reason rather than defaulting the
     * whole family check, and does not stop the rest of the family from
     * being evaluated.
     */
    @Transactional(readOnly = true)
    public Map<Long, EligibilityResult> evaluateFamily(Member principal, List<Member> dependents, LocalDate serviceDate) {
        Map<Long, EligibilityResult> results = new HashMap<>();
        List<Member> all = new ArrayList<>();
        all.add(principal);
        all.addAll(dependents);
        for (Member member : all) {
            results.put(member.getId(), evaluateMember(member, serviceDate));
        }
        return results;
    }

    private EligibilityResult evaluateMember(Member member, LocalDate serviceDate) {
        EligibilityCheckRequest request = EligibilityCheckRequest.builder()
                .memberId(member.getId())
                .serviceDate(serviceDate != null ? serviceDate : LocalDate.now())
                .build();

        try {
            return eligibilityService.checkEligibility(request);
        } catch (Exception e) {
            log.error("[FamilyEligibility] Error checking eligibility for member {}: {}", member.getId(), e.getMessage());
            return EligibilityResult.notEligible(
                    UUID.randomUUID().toString(),
                    null,
                    List.of(EligibilityResult.ReasonDetail.builder()
                            .code("SYSTEM_ERROR")
                            .messageAr("حدث خطأ أثناء التحقق من الأهلية")
                            .messageEn("Error during eligibility check")
                            .details(e.getMessage())
                            .hardFailure(true)
                            .build()),
                    0L,
                    0);
        }
    }

    private FamilyMemberEligibility toFamilyMemberEligibility(Member member, EligibilityResult result) {
        String reason = null;
        String reasonAr = null;
        if (!result.isEligible() && result.getReasons() != null && !result.getReasons().isEmpty()) {
            EligibilityResult.ReasonDetail firstReason = result.getReasons().get(0);
            reason = firstReason.getDetails();
            reasonAr = firstReason.getMessageAr();
        }

        String relationshipType = "PRINCIPAL";
        if (member.getParent() != null) {
            relationshipType = member.getRelationship() != null ?
                    member.getRelationship().name() : "DEPENDENT";
        }

        return FamilyMemberEligibility.builder()
                .memberId(member.getId())
                .memberName(member.getFullName())
                .memberNameAr(member.getFullName()) // Using fullName for both (Arabic stored in fullName)
                .civilId(member.getNationalNumber())
                .cardNumber(member.getCardNumber())
                .relationshipType(relationshipType)
                .memberStatus(member.getStatus() != null ? member.getStatus().name() : null)
                .dateOfBirth(member.getBirthDate())
                .gender(member.getGender() != null ? member.getGender().name() : null)
                .eligible(result.isEligible())
                .eligibilityStatus(result.getStatus() != null ? result.getStatus().name() : null)
                .reason(reason)
                .reasonAr(reasonAr)
                .policyNumber(result.getSnapshot() != null ? result.getSnapshot().getPolicyNumber() : null)
                .coverageStart(result.getSnapshot() != null ? result.getSnapshot().getCoverageStart() : null)
                .coverageEnd(result.getSnapshot() != null ? result.getSnapshot().getCoverageEnd() : null)
                .employerName(result.getSnapshot() != null ? result.getSnapshot().getEmployerName() :
                        (member.getEmployer() != null ? member.getEmployer().getName() : null))
                .build();
    }
}

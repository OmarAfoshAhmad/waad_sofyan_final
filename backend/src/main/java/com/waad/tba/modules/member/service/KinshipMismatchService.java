package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.dto.KinshipMismatchDto;
import com.waad.tba.modules.member.dto.KinshipMismatchFixRequest;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import com.waad.tba.modules.member.service.CardNumberGeneratorService;
import com.waad.tba.modules.member.service.GenderInferenceUtil;

@Service
@RequiredArgsConstructor
@Slf4j
public class KinshipMismatchService {

    private final MemberRepository memberRepository;
    private final CardNumberGeneratorService cardNumberGeneratorService;

    @Transactional(readOnly = true)
    public List<KinshipMismatchDto> findMismatches() {
        List<Member> dependents = memberRepository.findPotentialMismatches();

        List<KinshipMismatchDto> mismatches = new ArrayList<>();

        for (Member member : dependents) {
            Member.Relationship rel = member.getRelationship();
            Member.Gender currentGender = member.getGender() != null ? member.getGender() : Member.Gender.UNDEFINED;
            
            // Expected gender based on relationship
            Member.Gender expectedFromRel = getExpectedGenderFromRelation(rel);
            
            // Case 0: Invalid Relationship in the system
            if (rel == Member.Relationship.SISTER || rel == Member.Relationship.BROTHER) {
                mismatches.add(new KinshipMismatchDto(
                        member.getId(),
                        member.getFullName(),
                        rel,
                        currentGender,
                        GenderInferenceUtil.inferGender(member.getFullName()),
                        "صلة القرابة المدخلة (" + (rel == Member.Relationship.SISTER ? "أخت" : "أخ") + ") غير مدعومة في نظام التأمينات"
                ));
                continue;
            }

            // Case 1: Database explicitly has a mismatch
            if (currentGender != Member.Gender.UNDEFINED && expectedFromRel != null && currentGender != expectedFromRel) {
                mismatches.add(new KinshipMismatchDto(
                        member.getId(),
                        member.getFullName(),
                        rel,
                        currentGender,
                        currentGender, // inferred is same, just explicit mismatch
                        "تعارض صريح بين الجنس المحدد وصلة القرابة"
                ));
                continue;
            }

            // Case 2: Implicit mismatch (Gender inferred from name contradicts relationship or explicit gender)
            Member.Gender inferredGender = GenderInferenceUtil.inferGender(member.getFullName());
            
            if (inferredGender != Member.Gender.UNDEFINED) {
                boolean contradictsRelation = (expectedFromRel != null && inferredGender != expectedFromRel);
                boolean contradictsExplicitGender = (currentGender != Member.Gender.UNDEFINED && inferredGender != currentGender);

                if (contradictsRelation || contradictsExplicitGender) {
                    mismatches.add(new KinshipMismatchDto(
                            member.getId(),
                            member.getFullName(),
                            rel,
                            currentGender,
                            inferredGender,
                            "الاسم (" + inferredGender.name() + ") يتناقض مع الجنس أو صلة القرابة المحددة (" + (rel != null ? rel.name() : "غير محدد") + ")"
                    ));
                    continue;
                }
            }

            // Case 3: Card Number suffix contradicts Relationship
            if (member.getCardNumber() != null && rel != null) {
                String card = member.getCardNumber().toUpperCase();
                boolean cardMismatch = false;
                if (rel == Member.Relationship.HUSBAND && !card.matches(".*H\\d+$")) cardMismatch = true;
                if (rel == Member.Relationship.WIFE && !card.matches(".*W\\d+$")) cardMismatch = true;
                if (rel == Member.Relationship.SON && !card.matches(".*S\\d+$")) cardMismatch = true;
                if (rel == Member.Relationship.DAUGHTER && !card.matches(".*D\\d+$")) cardMismatch = true;
                if (rel == Member.Relationship.FATHER && !card.matches(".*F\\d+$")) cardMismatch = true;
                if (rel == Member.Relationship.MOTHER && !card.matches(".*M\\d+$")) cardMismatch = true;

                if (cardMismatch) {
                    mismatches.add(new KinshipMismatchDto(
                            member.getId(),
                            member.getFullName(),
                            rel,
                            currentGender,
                            inferredGender != Member.Gender.UNDEFINED ? inferredGender : currentGender,
                            "رقم البطاقة (" + member.getCardNumber() + ") لا يتوافق مع صلة القرابة المحددة (" + rel.name() + ")"
                    ));
                }
            }
        }

        return mismatches;
    }

    private Member.Gender getExpectedGenderFromRelation(Member.Relationship rel) {
        if (rel == null) return null;
        return switch (rel) {
            case SON, HUSBAND, FATHER -> Member.Gender.MALE;
            case DAUGHTER, WIFE, MOTHER -> Member.Gender.FEMALE;
            default -> null;
        };
    }

    @Transactional
    public void fixMismatch(Long memberId, KinshipMismatchFixRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        if (request.getNewRelationship() != null) {
            member.setRelationship(request.getNewRelationship());
        }
        if (request.getNewGender() != null) {
            member.setGender(request.getNewGender());
        }
        member.setKinshipVerified(true);
        memberRepository.save(member);

        // After saving the fix, resequence dependents for the parent to ensure birthDate ordering
        if (member.isDependent() && member.getParent() != null) {
            cardNumberGeneratorService.resequenceDependents(member.getParent());
        }
    }

    @Transactional
    public void ignoreMismatch(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        member.setKinshipVerified(true);
        memberRepository.save(member);
    }

    @Transactional
    public void fixMismatchesBulk(com.waad.tba.modules.member.dto.KinshipMismatchBulkFixRequest request) {
        if (request.getMemberIds() == null || request.getMemberIds().isEmpty()) {
            return;
        }
        List<Member> members = memberRepository.findAllById(request.getMemberIds());
        for (Member member : members) {
            if (request.getNewRelationship() != null) {
                member.setRelationship(request.getNewRelationship());
            }
            if (request.getNewGender() != null) {
                member.setGender(request.getNewGender());
            }
            member.setKinshipVerified(true);
        }
        memberRepository.saveAll(members);

        // Resequence for each distinct parent to ensure birthDate ordering
        members.stream()
            .filter(m -> m.isDependent() && m.getParent() != null)
            .map(Member::getParent)
            .distinct()
            .forEach(cardNumberGeneratorService::resequenceDependents);
    }

    @Transactional
    public void ignoreMismatchesBulk(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }
        List<Member> members = memberRepository.findAllById(memberIds);
        for (Member member : members) {
            member.setKinshipVerified(true);
        }
        memberRepository.saveAll(members);
    }
}

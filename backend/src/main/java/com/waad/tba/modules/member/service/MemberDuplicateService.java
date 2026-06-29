package com.waad.tba.modules.member.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.dto.MemberDuplicateGroupDto;
import com.waad.tba.modules.member.dto.MemberDuplicateGroupDto.DuplicateMemberInfo;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberAttributeRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthEmailRequestRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.repository.VisitRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberDuplicateService {

    private final MemberRepository memberRepository;
    private final VisitRepository visitRepository;
    private final ClaimRepository claimRepository;
    private final PreAuthEmailRequestRepository preAuthEmailRequestRepository;
    private final MemberAttributeRepository memberAttributeRepository;
    private final CardNumberGeneratorService cardNumberGeneratorService;

    /**
     * Finds duplicate members across the entire system.
     * Groups them by Normalized Name + Employer (for principals) or Normalized Name + Parent (for dependents).
     */
    @Transactional(readOnly = true)
    public List<MemberDuplicateGroupDto> findDuplicates() {
        // Fetch all active members using lightweight projection to avoid N+1 and slow initialization
        List<com.waad.tba.modules.member.dto.MemberLightProjection> activeMembers = memberRepository.findAllActiveMembersLight(Member.MemberStatus.ACTIVE);

        Map<String, MemberDuplicateGroupDto> duplicateGroupsMap = new HashMap<>();

        for (com.waad.tba.modules.member.dto.MemberLightProjection m : activeMembers) {
            String normalizedName = normalizeText(m.getFullName());
            if (normalizedName == null || normalizedName.isBlank()) continue;

            String groupKey;
            // A member is principal if parent is null
            boolean isPrincipal = m.getParent() == null;
            Long employerId = null;
            Long parentId = null;

            if (isPrincipal) {
                if (m.getEmployer() == null) continue;
                employerId = m.getEmployer().getId();
                groupKey = "P::" + employerId + "::" + normalizedName;
            } else {
                if (m.getParent() == null) continue;
                parentId = m.getParent().getId();
                employerId = m.getEmployer() != null ? m.getEmployer().getId() : null;
                groupKey = "D::" + parentId + "::" + normalizedName;
            }

            if (!duplicateGroupsMap.containsKey(groupKey)) {
                duplicateGroupsMap.put(groupKey, MemberDuplicateGroupDto.builder()
                        .normalizedName(normalizedName)
                        .isPrincipal(isPrincipal)
                        .employerId(employerId)
                        .parentId(parentId)
                        .members(new ArrayList<>())
                        .build());
            }
            MemberDuplicateGroupDto group = duplicateGroupsMap.get(groupKey);

            group.getMembers().add(DuplicateMemberInfo.builder()
                .id(m.getId())
                .fullName(m.getFullName())
                .cardNumber(m.getCardNumber())
                .nationalNumber(m.getNationalNumber())
                .birthDate(m.getBirthDate())
                .createdAt(m.getCreatedAt())
                .relationship(m.getRelationship() != null ? m.getRelationship().name() : null)
                .gender(m.getGender() != null ? m.getGender().name() : null)
                .build());
        }

        // Return only groups with > 1 member
        List<MemberDuplicateGroupDto> duplicates = duplicateGroupsMap.values().stream()
                .filter(g -> g.getMembers().size() > 1)
                .collect(Collectors.toList());

        // Optimize: Enrich counts and lazy fields ONLY for actual duplicates to avoid N+1 over 100k members
        for (MemberDuplicateGroupDto group : duplicates) {
            Member firstMember = memberRepository.findById(group.getMembers().get(0).getId()).orElse(null);
            if (firstMember != null) {
                if (group.isPrincipal() && firstMember.getEmployer() != null) {
                    group.setEmployerName(firstMember.getEmployer().getName());
                } else if (!group.isPrincipal() && firstMember.getParent() != null) {
                    group.setParentCardNumber(firstMember.getParent().getCardNumber());
                    group.setParentName(firstMember.getParent().getFullName());
                }
            }

            for (DuplicateMemberInfo mInfo : group.getMembers()) {
                mInfo.setVisitCount(visitRepository.findByMemberId(mInfo.getId()).size());
                mInfo.setClaimCount(claimRepository.findByMemberId(mInfo.getId()).size());
                if (group.isPrincipal()) {
                    List<Member> deps = memberRepository.findByParentId(mInfo.getId());
                    mInfo.setDependentCount(deps.size());
                    mInfo.setDependentNames(deps.stream().map(Member::getFullName).collect(Collectors.toList()));
                } else {
                    mInfo.setDependentCount(0);
                    mInfo.setDependentNames(new ArrayList<>());
                }
            }
        }

        return duplicates;
    }

    /**
     * Merges duplicate members into the primary member.
     * Transfers all relations (Visits, Claims, PreAuths, Dependents) and soft-deletes the duplicates.
     */
    @Transactional
    public void mergeDuplicates(Long primaryMemberId, List<Long> duplicateMemberIds) {
        log.info("Merging duplicates {} into primary member {}", duplicateMemberIds, primaryMemberId);

        if (duplicateMemberIds == null || duplicateMemberIds.isEmpty()) return;

        Member primary = memberRepository.findById(primaryMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Primary member not found: " + primaryMemberId));

        for (Long dupId : duplicateMemberIds) {
            if (dupId.equals(primaryMemberId)) continue; // skip self

            Member duplicate = memberRepository.findById(dupId).orElse(null);
            if (duplicate == null) continue;

            // 1. Transfer Visits
            List<Visit> visits = visitRepository.findByMemberId(dupId);
            for (Visit v : visits) {
                v.setMember(primary);
            }
            visitRepository.saveAll(visits);

            // 2. Transfer Claims
            List<Claim> claims = claimRepository.findByMemberId(dupId);
            for (Claim c : claims) {
                c.setMember(primary);
            }
            claimRepository.saveAll(claims);

            // 3. Transfer Dependents (if duplicate was a principal)
            if (duplicate.isPrincipal()) {
                List<Member> dependents = memberRepository.findByParentId(dupId);
                for (Member dep : dependents) {
                    dep.setParent(primary);
                }
                memberRepository.saveAll(dependents);
            }

            // 4. Update PreAuths
            preAuthEmailRequestRepository.updateMemberId(dupId, primaryMemberId);
            
            // 5. Delete Member Attributes to avoid FK constraints during duplicate deletion
            memberAttributeRepository.deleteByMemberId(dupId);

            // 6. Delete or Soft Delete Duplicate
            // We will hard delete them to clean up the system since all relations are transferred!
            memberRepository.delete(duplicate);
            log.info("Transferred {} visits, {} claims, and deleted duplicate member {}", visits.size(), claims.size(), dupId);
        }

        // Resequence card numbers for dependents of the same parent if a dependent was merged
        if (primary.isDependent() && primary.getParent() != null) {
            cardNumberGeneratorService.resequenceDependents(primary.getParent());
        }

        log.info("Merge completed for primary member {}", primaryMemberId);
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        return text.trim().toLowerCase()
                .replaceAll("[أإآ]", "ا")
                .replaceAll("ة", "ه")
                .replaceAll("ى", "ي")
                .replaceAll("\\s+", " ");
    }
}

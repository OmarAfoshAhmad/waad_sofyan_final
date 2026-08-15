package com.waad.tba.modules.member.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.dto.MemberDuplicateGroupDto;
import com.waad.tba.modules.member.dto.MemberDuplicateGroupDto.DuplicateMemberInfo;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.MemberCommandAccessPolicy;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.modules.member.entity.StatusSource;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.waad.tba.common.exception.BusinessRuleException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberDuplicateService {

    private final MemberRepository memberRepository;
    private final VisitRepository visitRepository;
    private final ClaimRepository claimRepository;
    private final MemberCommandAccessPolicy commandAccessPolicy;
    private final MemberStatusTransitionService statusTransitionService;
    private final MemberIdentityResolver identityResolver;
    private final AuthorizationService authorizationService;
    private final JdbcTemplate jdbc;

    /**
     * Finds duplicate members across the entire system.
     * Groups them by Normalized Name + Employer (for principals) or Normalized Name + Parent (for dependents).
     */
    @Transactional(readOnly = true)
    public List<MemberDuplicateGroupDto> findDuplicates() {
        commandAccessPolicy.require(MemberOperation.RESOLVE_DUPLICATES, null);
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
     * Retires duplicate identities without moving or deleting any historical row.
     */
    @Transactional
    public void mergeDuplicates(Long primaryMemberId, List<Long> duplicateMemberIds, String reason,
            Map<Long, Long> expectedVersions) {
        commandAccessPolicy.require(MemberOperation.RESOLVE_DUPLICATES, null);
        if (reason == null || reason.isBlank()) throw new BusinessRuleException("سبب دمج المكرر إلزامي");
        if (duplicateMemberIds == null || duplicateMemberIds.isEmpty())
            throw new BusinessRuleException("يجب تحديد سجل مكرر واحد على الأقل");
        if (expectedVersions == null) throw new BusinessRuleException("نسخ السجلات مطلوبة");

        Long canonicalPrimaryId = identityResolver.resolveCanonicalId(primaryMemberId);
        if (!Objects.equals(canonicalPrimaryId, primaryMemberId))
            throw new BusinessRuleException("السجل الأساسي المحدد مكرر بدوره؛ اختر السجل النهائي");

        List<Long> ids = java.util.stream.Stream.concat(java.util.stream.Stream.of(primaryMemberId),
                duplicateMemberIds.stream()).distinct().sorted().toList();
        if (ids.size() != duplicateMemberIds.size() + 1 || duplicateMemberIds.contains(primaryMemberId))
            throw new BusinessRuleException("لا يجوز تكرار المعرفات أو دمج السجل في نفسه");
        if (!expectedVersions.keySet().equals(new java.util.HashSet<>(ids)))
            throw new BusinessRuleException("يجب إرسال نسخة السجل الأساسي وكل سجل مكرر");

        Map<Long, Member> locked = new java.util.LinkedHashMap<>();
        for (Long id : ids) locked.put(id, memberRepository.findByIdWithLock(id)
                .orElseThrow(() -> new BusinessRuleException("أحد سجلات الدمج غير موجود")));
        Member primary = locked.get(primaryMemberId);
        User actor = authorizationService.requireCurrentUser();
        for (Member member : locked.values()) {
            if (!Objects.equals(member.getVersion(), expectedVersions.get(member.getId())))
                throw new BusinessRuleException("تم تعديل أحد السجلات؛ حدّث الصفحة وأعد المحاولة");
        }

        for (Long duplicateId : duplicateMemberIds) {
            Member duplicate = locked.get(duplicateId);
            assertCompatible(primary, duplicate);
            if (jdbc.queryForObject("select exists(select 1 from member_merge_records where duplicate_member_id=?)",
                    Boolean.class, duplicateId)) throw new BusinessRuleException("السجل المكرر مدمج مسبقاً");

            jdbc.update("""
                    insert into member_merge_records(merge_id,duplicate_member_id,primary_member_id,reason,merged_by,
                        duplicate_name,duplicate_card_number,primary_name,primary_card_number)
                    values (?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), duplicateId, primaryMemberId, reason.trim(), actor.getId(),
                    duplicate.getFullName(), duplicate.getCardNumber(), primary.getFullName(), primary.getCardNumber());
            statusTransitionService.transitionTo(duplicate, Member.MemberStatus.DUPLICATE_MERGED,
                    reason.trim(), StatusSource.SYSTEM, UUID.randomUUID().toString(), actor.getId());
        }
        log.info("Retired duplicate identities {} in favour of {} without moving history", duplicateMemberIds, primaryMemberId);
    }

    private static void assertCompatible(Member primary, Member duplicate) {
        if (primary.isPrincipal() != duplicate.isPrincipal())
            throw new BusinessRuleException("لا يمكن دمج رئيس أسرة مع تابع");
        if (!Objects.equals(primary.getEmployer().getId(), duplicate.getEmployer().getId()))
            throw new BusinessRuleException("لا يمكن دمج مستفيدين من جهتي عمل مختلفتين");
        if (primary.isDependent() && !Objects.equals(primary.getParent().getId(), duplicate.getParent().getId()))
            throw new BusinessRuleException("لا يمكن دمج تابعين من أسرتين مختلفتين");
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

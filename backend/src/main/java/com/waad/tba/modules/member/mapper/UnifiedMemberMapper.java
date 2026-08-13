package com.waad.tba.modules.member.mapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import com.waad.tba.modules.member.dto.DependentMemberDto;
import com.waad.tba.modules.member.dto.DependentViewDto;
import com.waad.tba.modules.member.dto.FamilyEligibilityResponseDto;
import com.waad.tba.modules.member.dto.MemberCreateDto;
import com.waad.tba.modules.member.dto.MemberUpdateDto;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/**
 * ==================== UNIFIED MEMBER ARCHITECTURE ====================
 * Mapper for unified member structure (Principal + Dependent in same table).
 *
 * Handles mapping between:
 * - MemberCreateDto → Member Entity (Principal or Dependent)
 * - DependentMemberDto → Member Entity (Dependent only)
 * - Member Entity → MemberViewDto (with dependents if Principal)
 * - Member Entity → DependentViewDto (for Dependent display)
 * - Family → FamilyEligibilityResponseDto (Principal + Dependents)
 * =====================================================================
 */
@Component
@RequiredArgsConstructor
public class UnifiedMemberMapper {

    private final AuthorizationService authorizationService;

    /**
     * SECTION_02 HIGH finding #9: national ID and home address are masked for
     * external provider-portal users — every read endpoint previously
     * returned the same full PII payload to PROVIDER_STAFF as to internal
     * staff/employer admins. Internal roles are unaffected.
     */
    private boolean shouldMaskSensitiveFields() {
        User currentUser = authorizationService.getCurrentUser();
        return authorizationService.isProvider(currentUser);
    }

    private String maskNationalNumber(String nationalNumber) {
        if (!shouldMaskSensitiveFields()) {
            return nationalNumber;
        }
        if (nationalNumber == null || nationalNumber.length() < 4) {
            return "****";
        }
        return "****" + nationalNumber.substring(nationalNumber.length() - 4);
    }

    private String maskAddress(String address) {
        return shouldMaskSensitiveFields() ? null : address;
    }

    /**
     * Convert MemberCreateDto to Member entity.
     * NOTE: This does NOT set barcode, cardNumber, parent, relationship.
     * Those are set by the service layer.
     */
    public Member toEntity(MemberCreateDto dto) {
        return Member.builder()
                .fullName(dto.getFullName())
                .nationalNumber(dto.getNationalNumber())
                .birthDate(dto.getBirthDate())
                .gender(dto.getGender())
                .maritalStatus(dto.getMaritalStatus())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .address(dto.getAddress())
                .nationality(dto.getNationality())
                .policyNumber(dto.getPolicyNumber())
                .employeeNumber(dto.getEmployeeNumber())
                .joinDate(dto.getJoinDate())
                .occupation(dto.getOccupation())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .cardStatus(dto.getCardStatus() != null ? dto.getCardStatus() : Member.CardStatus.ACTIVE)
                .notes(dto.getNotes())
                .build();
    }

    /**
     * Convert DependentMemberDto to Member entity.
     * NOTE: This does NOT set barcode (always NULL), cardNumber (generated), parent
     * (set by service).
     */
    public Member toEntity(DependentMemberDto dto) {
        return Member.builder()
                .relationship(dto.getRelationship()) // REQUIRED for dependents
                .fullName(dto.getFullName())
                .nationalNumber(dto.getNationalNumber())
                .birthDate(dto.getBirthDate())
                .gender(dto.getGender() != null ? dto.getGender() : Member.Gender.UNDEFINED)
                .maritalStatus(dto.getMaritalStatus())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .occupation(dto.getOccupation())
                .notes(dto.getNotes())
                .cardStatus(Member.CardStatus.ACTIVE) // Default
                .build();
    }

    /**
     * Update Member entity from MemberUpdateDto.
     */
    public void updateEntityFromDto(Member entity, MemberUpdateDto dto) {
        if (dto.getFullName() != null) {
            entity.setFullName(dto.getFullName());
        }
        if (dto.getNationalNumber() != null) {
            entity.setNationalNumber(dto.getNationalNumber());
        }
        if (dto.getBirthDate() != null) {
            entity.setBirthDate(dto.getBirthDate());
        }
        if (dto.getGender() != null) {
            entity.setGender(dto.getGender());
        }
        if (dto.getMaritalStatus() != null) {
            entity.setMaritalStatus(dto.getMaritalStatus());
        }
        if (dto.getPhone() != null) {
            entity.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            entity.setEmail(dto.getEmail());
        }
        if (dto.getAddress() != null) {
            entity.setAddress(dto.getAddress());
        }
        if (dto.getNationality() != null) {
            entity.setNationality(dto.getNationality());
        }
        if (dto.getPolicyNumber() != null) {
            entity.setPolicyNumber(dto.getPolicyNumber());
        }
        if (dto.getEmployeeNumber() != null) {
            entity.setEmployeeNumber(dto.getEmployeeNumber());
        }
        if (dto.getJoinDate() != null) {
            entity.setJoinDate(dto.getJoinDate());
        }
        if (dto.getOccupation() != null) {
            entity.setOccupation(dto.getOccupation());
        }
        // Status is intentionally NOT settable through the generic update path: it must go
        // through UnifiedMemberService.changeStatus(), which enforces the reason-for-SUSPENDED
        // rule, syncs the `active` flag, cascades to dependents, and writes an audit log entry.
        // Setting it here would silently bypass all of that.
        if (dto.getStartDate() != null) {
            entity.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            entity.setEndDate(dto.getEndDate());
        }
        if (dto.getCardStatus() != null) {
            entity.setCardStatus(dto.getCardStatus());
        }
        if (dto.getBlockedReason() != null) {
            entity.setBlockedReason(dto.getBlockedReason());
        }
        if (dto.getNotes() != null) {
            entity.setNotes(dto.getNotes());
        }
        // status/active/benefitPolicy/employer/relationship/cardNumber are not
        // copied here at all. They are no longer merely "ignored" either --
        // UnifiedMemberService.rejectSensitiveFieldChanges refuses the request
        // outright when one of them would CHANGE, so a caller can never be told
        // their save succeeded while the change was quietly dropped.
    }

    /**
     * Convert Member entity to MemberViewDto (for PRINCIPAL with dependents).
     */
    public MemberViewDto toViewDto(Member entity, List<Member> dependents) {
        MemberViewDto dto = toViewDto(entity);

        // Add dependent information
        if (dependents != null && !dependents.isEmpty()) {
            List<DependentViewDto> dependentDtos = dependents.stream()
                    .map(this::toDependentViewDto)
                    .collect(Collectors.toList());
            dto.setDependents(dependentDtos);
            dto.setDependentsCount(dependentDtos.size());
        } else {
            dto.setDependentsCount(0);
        }

        return dto;
    }

    /**
     * Convert Member entity to MemberViewDto (single member).
     */
    public MemberViewDto toViewDto(Member entity) {
        MemberViewDto dto = MemberViewDto.builder()
                .id(entity.getId())
                .type(entity.getType().name()) // PRINCIPAL or DEPENDENT
                .fullName(entity.getFullName())
                .nationalNumber(maskNationalNumber(entity.getNationalNumber()))
                .cardNumber(entity.getCardNumber())
                .barcode(entity.getBarcode()) // NULL for dependents
                .birthDate(entity.getBirthDate())
                .gender(entity.getGender())
                .maritalStatus(entity.getMaritalStatus())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .address(maskAddress(entity.getAddress()))
                .nationality(entity.getNationality())
                .policyNumber(entity.getPolicyNumber())
                .employeeNumber(entity.getEmployeeNumber())
                .joinDate(entity.getJoinDate())
                .occupation(entity.getOccupation())
                .status(entity.getStatus())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .cardStatus(entity.getCardStatus())
                .blockedReason(entity.getBlockedReason())
                .statusReason(entity.getStatusReason())
                .statusSource(entity.getStatusSource())
                .statusChangedAt(entity.getStatusChangedAt())
                .previousStatus(entity.getPreviousStatus())
                .statusTransitionId(entity.getStatusTransitionId())
                .eligibilityStatus(entity.getEligibilityStatus())
                .photoUrl(entity.getPhotoUrl())
                .profilePhotoPath(entity.getProfilePhotoPath())
                .notes(entity.getNotes())
                .active(entity.getActive())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();

        // Set organization/policy info
        if (entity.getEmployer() != null) {
            dto.setEmployerId(entity.getEmployer().getId());
            dto.setEmployerName(entity.getEmployer().getName());
        }

        if (entity.getBenefitPolicy() != null) {
            dto.setBenefitPolicyId(entity.getBenefitPolicy().getId());
            dto.setBenefitPolicyName(entity.getBenefitPolicy().getName());
            dto.setBenefitPolicyCode(entity.getBenefitPolicy().getPolicyCode());
            dto.setBenefitPolicyStatus(entity.getBenefitPolicy().getStatus().name());
        }

        // Set parent/relationship info if dependent
        if (entity.isDependent()) {
            if (entity.getParent() != null) {
                dto.setParentId(entity.getParent().getId());
                dto.setParentFullName(entity.getParent().getFullName());
            } 
            dto.setRelationship(entity.getRelationship());
        }

        return dto;
    }

    /**
     * Convert Member entity to DependentViewDto (for dependent display).
     */
    public DependentViewDto toDependentViewDto(Member entity) {
        if (!entity.isDependent()) {
            throw new IllegalArgumentException("Cannot convert principal to DependentViewDto");
        }

        return DependentViewDto.builder()
                .id(entity.getId())
                .relationship(entity.getRelationship())
                .fullName(entity.getFullName())
                .nationalNumber(maskNationalNumber(entity.getNationalNumber()))
                .cardNumber(entity.getCardNumber())
                .birthDate(entity.getBirthDate())
                .gender(entity.getGender())
                .maritalStatus(entity.getMaritalStatus())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .occupation(entity.getOccupation())
                .status(entity.getStatus())
                .active(entity.getActive())
                .eligibilityStatus(entity.getEligibilityStatus())
                .notes(entity.getNotes())
                .photoUrl(entity.getPhotoUrl())
                .profilePhotoPath(entity.getProfilePhotoPath())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .parentId(entity.getParent() != null ? entity.getParent().getId() : null)
                .parentFullName(entity.getParent() != null ? entity.getParent().getFullName() : null)
                .familyBarcode(entity.getFamilyBarcode()) // Inherited from principal
                .build();
    }

    /**
     * Convert Principal + Dependents to FamilyEligibilityResponseDto.
     *
     * @param eligibilityByMemberId Real-time eligibility engine results
     *                              (see EligibilityEngineService), one per
     *                              family member, keyed by member id. This
     *                              is now the single source of truth for the
     *                              "eligible" decision -- previously this
     *                              method computed its own shallow
     *                              active+cachedFlag+hasEmployer check that
     *                              never consulted the real coverage rules
     *                              engine, so a member the engine would
     *                              reject (e.g. exhausted limit, inactive
     *                              policy) could still be reported eligible
     *                              here.
     */
    public FamilyEligibilityResponseDto toFamilyEligibilityResponse(
            Member principal, List<Member> dependents, Map<Long, EligibilityResult> eligibilityByMemberId) {
        // Convert principal
        MemberViewDto principalDto = toViewDto(principal);

        // Convert dependents
        List<DependentViewDto> dependentDtos = dependents.stream()
                .map(this::toDependentViewDto)
                .collect(Collectors.toList());

        boolean principalHasEmployer = principal.getEmployer() != null;

        int eligibleCount = 0;
        Map<Long, String> ineligibilityReasonsAr = new java.util.HashMap<>();
        java.util.Set<Long> systemErrorMemberIds = new java.util.HashSet<>();
        List<Member> allMembers = new java.util.ArrayList<>(dependents.size() + 1);
        allMembers.add(principal);
        allMembers.addAll(dependents);
        for (Member member : allMembers) {
            EligibilityResult result = eligibilityByMemberId.get(member.getId());
            if (result != null && result.isEligible()) {
                eligibleCount++;
                continue;
            }
            // Not eligible (or no result at all -- fails closed): record why, and
            // whether it's a genuine rule denial vs. the engine call itself
            // failing, so the frontend can tell "this member isn't covered" apart
            // from "we couldn't verify this member, try again" rather than
            // presenting both identically as a plain denial.
            if (result != null && result.getReasons() != null && !result.getReasons().isEmpty()) {
                var firstReason = result.getReasons().get(0);
                ineligibilityReasonsAr.put(member.getId(), firstReason.getMessageAr());
                if ("SYSTEM_ERROR".equals(firstReason.getCode())) {
                    systemErrorMemberIds.add(member.getId());
                }
            }
        }

        boolean eligible = eligibleCount > 0 && principalHasEmployer;
        String message;
        if (!principalHasEmployer) {
            message = "العائلة غير مؤهلة - المؤمن عليه غير مرتبط بجهة عمل";
        } else if (eligible) {
            message = String.format("العائلة مؤهلة - %d من %d أعضاء مؤهلين", eligibleCount, 1 + dependents.size());
        } else {
            message = "جميع أفراد العائلة غير مؤهلين";
        }

        return FamilyEligibilityResponseDto.builder()
                .eligible(eligible)
                .message(message)
                .principal(principalDto)
                .dependents(dependentDtos)
                .totalFamilyMembers(1 + dependents.size())
                .eligibleMembersCount(eligibleCount)
                .familyBarcode(principal.getBarcode())
                .benefitPolicyId(principal.getBenefitPolicy() != null ? principal.getBenefitPolicy().getId() : null)
                .benefitPolicyName(principal.getBenefitPolicy() != null ? principal.getBenefitPolicy().getName() : null)
                .benefitPolicyStatus(
                        principal.getBenefitPolicy() != null ? principal.getBenefitPolicy().getStatus().name() : null)
                .employerOrgId(principal.getEmployer() != null ? principal.getEmployer().getId() : null)
                .employerOrgName(principal.getEmployer() != null ? principal.getEmployer().getName() : null)
                .ineligibilityReasonsAr(ineligibilityReasonsAr)
                .systemErrorMemberIds(systemErrorMemberIds)
                .build();
    }
}

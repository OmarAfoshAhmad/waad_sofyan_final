package com.waad.tba.modules.member.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.waad.tba.modules.member.dto.DependentMemberDto;
import com.waad.tba.modules.member.dto.DependentViewDto;
import com.waad.tba.modules.member.dto.FamilyEligibilityResponseDto;
import com.waad.tba.modules.member.dto.MemberCreateDto;
import com.waad.tba.modules.member.dto.MemberUpdateDto;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.entity.Member;

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
public class UnifiedMemberMapper {

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
                .status(dto.getStatus() != null ? dto.getStatus() : Member.MemberStatus.ACTIVE)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .cardStatus(dto.getCardStatus() != null ? dto.getCardStatus() : Member.CardStatus.ACTIVE)
                .notes(dto.getNotes())
                .active(dto.getActive() != null ? dto.getActive() : true)
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
                .active(dto.getActive() != null ? dto.getActive() : true)
                .status(Member.MemberStatus.ACTIVE) // Default
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
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
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
        if (dto.getActive() != null) {
            entity.setActive(dto.getActive());
        }

        // Relationship can be updated for dependents only
        if (dto.getRelationship() != null && entity.isDependent()) {
            entity.setRelationship(dto.getRelationship());
        }
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
                .nationalNumber(entity.getNationalNumber())
                .cardNumber(entity.getCardNumber())
                .barcode(entity.getBarcode()) // NULL for dependents
                .birthDate(entity.getBirthDate())
                .gender(entity.getGender())
                .maritalStatus(entity.getMaritalStatus())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .address(entity.getAddress())
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
                .nationalNumber(entity.getNationalNumber())
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
     */
    public FamilyEligibilityResponseDto toFamilyEligibilityResponse(Member principal, List<Member> dependents) {
        // Convert principal
        MemberViewDto principalDto = toViewDto(principal);

        // Convert dependents
        List<DependentViewDto> dependentDtos = dependents.stream()
                .map(this::toDependentViewDto)
                .collect(Collectors.toList());

        // Count eligible members - safely handle null values
        int eligibleCount = 0;
        boolean principalActive = Boolean.TRUE.equals(principal.getActive());
        boolean principalEligible = Boolean.TRUE.equals(principal.getEligibilityStatus());
        boolean principalHasEmployer = principal.getEmployer() != null;

        // Principal is eligible if active, eligible status is true, and has employer
        if (principalActive && principalEligible && principalHasEmployer) {
            eligibleCount++;
        }

        eligibleCount += (int) dependents.stream()
                .filter(d -> Boolean.TRUE.equals(d.getActive()) && Boolean.TRUE.equals(d.getEligibilityStatus()))
                .count();

        // Determine eligibility - also check if principal has employer
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
                .build();
    }
}

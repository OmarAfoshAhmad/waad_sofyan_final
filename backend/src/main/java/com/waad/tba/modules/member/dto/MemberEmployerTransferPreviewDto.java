package com.waad.tba.modules.member.dto;

import java.util.List;

/** Read-only impact preview for {@link MemberEmployerTransferRequest}, shown before confirmation. */
public record MemberEmployerTransferPreviewDto(
        Long principalId,
        Long currentEmployerId,
        String currentEmployerName,
        Long newEmployerId,
        String newEmployerName,
        List<FamilyMemberSnapshot> familyMembers) {

    public record FamilyMemberSnapshot(
            Long memberId,
            String fullName,
            boolean principal,
            String relationship,
            Long version) {
    }
}

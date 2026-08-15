package com.waad.tba.modules.member.dto;

import com.waad.tba.modules.member.entity.Member.Relationship;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberRelationshipCorrectionRequest(
        @NotNull Relationship relationship,
        @NotBlank String reason,
        @NotNull Long expectedVersion) {
}

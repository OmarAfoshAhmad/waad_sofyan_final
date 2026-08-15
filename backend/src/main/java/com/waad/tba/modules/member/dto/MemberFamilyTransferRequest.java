package com.waad.tba.modules.member.dto;

import java.time.LocalDate;

import com.waad.tba.modules.member.entity.Member.Relationship;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

public record MemberFamilyTransferRequest(
        @NotNull Long newPrincipalId,
        @NotNull Relationship relationship,
        @NotNull @PastOrPresent LocalDate effectiveDate,
        @NotBlank String reason,
        @NotNull Long expectedVersion) {
}

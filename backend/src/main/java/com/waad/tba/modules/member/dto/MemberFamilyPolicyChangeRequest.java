package com.waad.tba.modules.member.dto;

import java.time.LocalDate;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

public record MemberFamilyPolicyChangeRequest(
        @NotNull Long policyId,
        @NotNull @PastOrPresent LocalDate effectiveDate,
        @NotBlank String reason,
        @NotEmpty Map<Long, Long> expectedVersions) {
}

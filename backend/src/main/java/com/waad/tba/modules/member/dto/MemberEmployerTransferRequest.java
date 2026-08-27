package com.waad.tba.modules.member.dto;

import java.time.LocalDate;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

/**
 * Moves a principal and their whole family to another employer as of an
 * explicit date. All-or-nothing: expectedVersions must name every family
 * member's current version or the whole call is rejected.
 *
 * newPolicyId is deliberately not inferred: either the caller names a
 * specific active policy of the new employer, or sets noPolicy=true to
 * confirm the family should carry no policy for now. Leaving both unset is
 * a validation error, not a silent "figure it out" default.
 */
public record MemberEmployerTransferRequest(
        @NotNull Long newEmployerId,
        Long newPolicyId,
        boolean noPolicy,
        @NotNull @PastOrPresent LocalDate effectiveDate,
        @NotBlank String reason,
        @NotEmpty Map<Long, Long> expectedVersions) {
}

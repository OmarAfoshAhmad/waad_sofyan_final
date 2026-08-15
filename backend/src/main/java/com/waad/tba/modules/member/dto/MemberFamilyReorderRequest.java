package com.waad.tba.modules.member.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;

public record MemberFamilyReorderRequest(
        @NotEmpty List<Long> dependentIds,
        @NotEmpty Map<Long, Long> expectedVersions) {
}

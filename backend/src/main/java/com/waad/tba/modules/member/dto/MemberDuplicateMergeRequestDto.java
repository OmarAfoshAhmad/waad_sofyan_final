package com.waad.tba.modules.member.dto;

import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberDuplicateMergeRequestDto {
    @NotNull private Long primaryMemberId;
    @NotEmpty private List<Long> duplicateMemberIds;
    @NotBlank private String reason;
    @NotEmpty private Map<Long, Long> expectedVersions;
}

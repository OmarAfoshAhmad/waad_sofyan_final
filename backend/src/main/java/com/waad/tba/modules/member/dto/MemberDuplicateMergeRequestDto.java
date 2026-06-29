package com.waad.tba.modules.member.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberDuplicateMergeRequestDto {
    private Long primaryMemberId;
    private List<Long> duplicateMemberIds;
}

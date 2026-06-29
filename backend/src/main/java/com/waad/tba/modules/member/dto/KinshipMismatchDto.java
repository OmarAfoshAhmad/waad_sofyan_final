package com.waad.tba.modules.member.dto;

import com.waad.tba.modules.member.entity.Member;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KinshipMismatchDto {
    private Long id;
    private String fullName;
    private Member.Relationship currentRelationship;
    private Member.Gender currentGender;
    private Member.Gender inferredGender;
    private String reason;
}

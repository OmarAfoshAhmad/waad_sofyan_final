package com.waad.tba.modules.member.dto;

import com.waad.tba.modules.member.entity.Member;

import lombok.Data;

@Data
public class KinshipMismatchFixRequest {
    private Member.Relationship newRelationship;
    private Member.Gender newGender;
}

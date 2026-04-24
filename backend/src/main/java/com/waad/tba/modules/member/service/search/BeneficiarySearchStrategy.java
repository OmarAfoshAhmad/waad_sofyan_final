package com.waad.tba.modules.member.service.search;

import com.waad.tba.modules.member.entity.Member;

import java.util.List;

public interface BeneficiarySearchStrategy {
    BeneficiarySearchType supportedType();

    List<Member> search(String value, Long employerId, Member.MemberStatus status, int size);
}

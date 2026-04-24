package com.waad.tba.modules.member.service.search;

import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ByIdBeneficiarySearchStrategy implements BeneficiarySearchStrategy {

    private final MemberRepository memberRepository;

    @Override
    public BeneficiarySearchType supportedType() {
        return BeneficiarySearchType.BY_ID;
    }

    @Override
    public List<Member> search(String value, Long employerId, Member.MemberStatus status, int size) {
        long id = Long.parseLong(value);
        Optional<Member> memberOpt = memberRepository.findById(id);

        if (memberOpt.isEmpty()) {
            return List.of();
        }

        Member member = memberOpt.get();

        if (member.getActive() != null && !member.getActive()) {
            return List.of();
        }

        if (employerId != null && (member.getEmployer() == null || !employerId.equals(member.getEmployer().getId()))) {
            return List.of();
        }

        if (status != null && member.getStatus() != status) {
            return List.of();
        }

        return List.of(member);
    }
}

package com.waad.tba.modules.member.service.search;

import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ByBarcodeBeneficiarySearchStrategy implements BeneficiarySearchStrategy {

    private final MemberRepository memberRepository;

    @Override
    public BeneficiarySearchType supportedType() {
        return BeneficiarySearchType.BY_BARCODE;
    }

    @Override
    public List<Member> search(String value, Long employerId, Member.MemberStatus status, int size) {
        String normalized = value.trim().toLowerCase();

        Specification<Member> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(cb.lower(root.get("barcode")), normalized));
            predicates.add(cb.or(cb.isNull(root.get("active")), cb.isTrue(root.get("active"))));

            if (employerId != null) {
                predicates.add(cb.equal(root.get("employer").get("id"), employerId));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return memberRepository
                .findAll(spec, PageRequest.of(0, Math.max(1, Math.min(size, 5)), Sort.by(Sort.Direction.ASC, "id")))
                .getContent();
    }
}

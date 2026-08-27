package com.waad.tba.modules.member.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** Resolves a retired duplicate identity for NEW operations only. */
@Service
@RequiredArgsConstructor
public class MemberIdentityResolver {
    private final JdbcTemplate jdbc;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public Long resolveCanonicalId(Long suppliedMemberId) {
        if (suppliedMemberId == null) throw new BusinessRuleException("معرّف المستفيد مطلوب");
        Long canonical = jdbc.queryForObject("""
                WITH RECURSIVE chain(id, depth) AS (
                    SELECT ?::bigint, 0
                    UNION ALL
                    SELECT r.primary_member_id, c.depth + 1
                      FROM chain c JOIN member_merge_records r ON r.duplicate_member_id = c.id
                     WHERE c.depth < 100
                ) SELECT id FROM chain ORDER BY depth DESC LIMIT 1
                """, Long.class, suppliedMemberId);
        return canonical;
    }

    @Transactional(readOnly = true)
    public Member resolveCanonicalOrFail(Long suppliedMemberId) {
        return memberRepository.findById(resolveCanonicalId(suppliedMemberId))
                .orElseThrow(() -> new BusinessRuleException("المستفيد غير موجود"));
    }
}

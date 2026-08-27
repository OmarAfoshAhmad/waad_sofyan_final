package com.waad.tba.modules.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.member.entity.MemberHardDeleteAudit;

@Repository
public interface MemberHardDeleteAuditRepository extends JpaRepository<MemberHardDeleteAudit, Long> {

    List<MemberHardDeleteAudit> findByMemberId(Long memberId);
}

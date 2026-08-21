package com.waad.tba.modules.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.member.entity.MemberImportRollbackSkip;

@Repository
public interface MemberImportRollbackSkipRepository extends JpaRepository<MemberImportRollbackSkip, Long> {

    List<MemberImportRollbackSkip> findByRollbackId(Long rollbackId);
}

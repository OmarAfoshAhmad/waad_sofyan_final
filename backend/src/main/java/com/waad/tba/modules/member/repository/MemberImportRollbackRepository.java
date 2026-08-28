package com.waad.tba.modules.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.member.entity.MemberImportRollback;
import com.waad.tba.modules.member.entity.MemberImportRollback.Status;

@Repository
public interface MemberImportRollbackRepository extends JpaRepository<MemberImportRollback, Long> {

    Optional<MemberImportRollback> findByImportLogIdAndStatus(Long importLogId, Status status);
}

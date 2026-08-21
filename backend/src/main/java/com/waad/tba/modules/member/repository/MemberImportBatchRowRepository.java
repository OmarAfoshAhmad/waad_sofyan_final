package com.waad.tba.modules.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.member.entity.MemberImportBatchRow;

@Repository
public interface MemberImportBatchRowRepository extends JpaRepository<MemberImportBatchRow, Long> {

    List<MemberImportBatchRow> findByImportLogId(Long importLogId);

    boolean existsByImportLogId(Long importLogId);
}

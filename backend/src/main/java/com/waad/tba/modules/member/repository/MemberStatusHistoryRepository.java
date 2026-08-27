package com.waad.tba.modules.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.member.entity.MemberStatusHistory;

/**
 * Read/insert only in practice -- the DB itself (V169 triggers) rejects
 * UPDATE/DELETE on this table regardless of what JpaRepository exposes.
 */
@Repository
public interface MemberStatusHistoryRepository extends JpaRepository<MemberStatusHistory, Long> {

    List<MemberStatusHistory> findByMemberIdOrderByChangedAtDesc(Long memberId);

    List<MemberStatusHistory> findByTransitionId(String transitionId);
}

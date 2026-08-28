package com.waad.tba.modules.member.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.waad.tba.modules.member.entity.MemberEmployerAssignment;

public interface MemberEmployerAssignmentRepository extends JpaRepository<MemberEmployerAssignment, Long> {

    @Query("""
            SELECT a FROM MemberEmployerAssignment a
            WHERE a.memberId = :memberId
              AND a.assignmentStartDate <= :date
              AND (a.assignmentEndDate IS NULL OR a.assignmentEndDate > :date)
            """)
    Optional<MemberEmployerAssignment> findCovering(@Param("memberId") Long memberId,
            @Param("date") LocalDate date);

    Optional<MemberEmployerAssignment> findByMemberIdAndAssignmentEndDateIsNull(Long memberId);

    List<MemberEmployerAssignment> findByMemberIdOrderByAssignmentStartDateDesc(Long memberId);

    @Query("SELECT a.memberId FROM MemberEmployerAssignment a WHERE a.memberId IN :memberIds")
    List<Long> findMemberIdsWithAnyAssignment(@Param("memberIds") Collection<Long> memberIds);

    /**
     * The bulk counterpart of {@link #findCovering}, for a page of members.
     * Same half-open window, so the two can never answer differently for the
     * same member and date.
     */
    @Query("""
            SELECT a FROM MemberEmployerAssignment a
            WHERE a.memberId IN :memberIds
              AND a.assignmentStartDate <= :date
              AND (a.assignmentEndDate IS NULL OR a.assignmentEndDate > :date)
            """)
    List<MemberEmployerAssignment> findCoveringForMembers(
            @Param("memberIds") Collection<Long> memberIds, @Param("date") LocalDate date);
}

package com.waad.tba.modules.member.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.member.entity.MemberPolicyAssignment;

@Repository
public interface MemberPolicyAssignmentRepository extends JpaRepository<MemberPolicyAssignment, Long> {

    /**
     * The assignment covering a specific date, using half-open [start, end)
     * semantics: start &lt;= date &lt; end, with a null end meaning open-ended.
     * V171's exclusion constraint guarantees at most one row can match.
     */
    @Query("""
            SELECT a FROM MemberPolicyAssignment a
            WHERE a.memberId = :memberId
              AND a.assignmentStartDate <= :date
              AND (a.assignmentEndDate IS NULL OR a.assignmentEndDate > :date)
            """)
    Optional<MemberPolicyAssignment> findCovering(@Param("memberId") Long memberId, @Param("date") LocalDate date);

    /**
     * The covering assignments for a whole set of members in one query.
     *
     * The list screen needs the dated policy for every row it shows, and
     * asking findCovering once per row makes the cost of the page a function
     * of how many rows it holds. This asks once for all of them.
     *
     * Returns rows rather than a map: a member with no covering assignment
     * simply has none here, and the caller has to decide what that means
     * rather than being handed a silent absence. It also returns every match,
     * so an overlap that V171's exclusion constraint should have prevented
     * surfaces as two rows to be refused instead of one row picked
     * arbitrarily.
     */
    @Query("""
            SELECT a FROM MemberPolicyAssignment a
            WHERE a.memberId IN :memberIds
              AND a.assignmentStartDate <= :date
              AND (a.assignmentEndDate IS NULL OR a.assignmentEndDate > :date)
            """)
    List<MemberPolicyAssignment> findCoveringForMembers(
            @Param("memberIds") java.util.Collection<Long> memberIds,
            @Param("date") LocalDate date);

    /** The currently open-ended assignment, if any. */
    Optional<MemberPolicyAssignment> findByMemberIdAndAssignmentEndDateIsNull(Long memberId);

    /**
     * Bulk counterpart for import: one query for a whole batch instead of one
     * per member, so recording assignments for a 30k-row import does not turn
     * into 30k lookups.
     */
    @Query("SELECT a.memberId FROM MemberPolicyAssignment a WHERE a.memberId IN :memberIds")
    List<Long> findMemberIdsWithAnyAssignment(@Param("memberIds") java.util.Collection<Long> memberIds);

    List<MemberPolicyAssignment> findByMemberIdOrderByAssignmentStartDateDesc(Long memberId);
}

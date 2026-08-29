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

    // ── employer-keyed, and deliberately named for the date they answer ────
    //
    // members.employer_id is a denormalised CURRENT pointer, kept for display
    // and for fast lookups. It is not the source of truth for whether a member
    // belongs to an employer -- these assignments are -- so a lifecycle
    // decision that reads the pointer is reading a cache of an answer instead
    // of the answer.
    //
    // The names carry their temporal meaning on purpose. A method called
    // countByEmployer would be reached for by the next person needing a
    // different question, and they would silently get this one's answer.

    /**
     * Members whose employer assignment COVERS this date and who are themselves
     * active. This is the "does anyone still belong to this employer?" question
     * that blocks archiving.
     *
     * DISTINCT because a member can hold more than one row for an employer
     * across time -- two closed windows and one open one is one member, not
     * three.
     */
    @Query("""
            SELECT COUNT(DISTINCT a.memberId) FROM MemberEmployerAssignment a
            WHERE a.employerId = :employerId
              AND a.assignmentStartDate <= :date
              AND (a.assignmentEndDate IS NULL OR a.assignmentEndDate > :date)
              AND EXISTS (SELECT 1 FROM Member m WHERE m.id = a.memberId AND m.active = true)
            """)
    long countActiveMembersAssignedOn(@Param("employerId") Long employerId, @Param("date") LocalDate date);

    /**
     * Whether this employer has EVER had a member assigned, at any date.
     *
     * A different question from the one above, and never a substitute for it:
     * an employer with a long history and nobody left today is exactly the
     * employer archiving exists for.
     */
    @Query("SELECT COUNT(a) > 0 FROM MemberEmployerAssignment a WHERE a.employerId = :employerId")
    boolean hasEverHadAnAssignedMember(@Param("employerId") Long employerId);

    /**
     * Which members belong to this employer on this date.
     *
     * Ids rather than entities: the callers that need this are deciding about
     * a whole employer's roster -- a replacement import terminating everyone
     * absent from a file, for one -- and loading every member to find out who
     * they are would be the wrong shape at that size.
     */
    @Query("""
            SELECT DISTINCT a.memberId FROM MemberEmployerAssignment a
            WHERE a.employerId = :employerId
              AND a.assignmentStartDate <= :date
              AND (a.assignmentEndDate IS NULL OR a.assignmentEndDate > :date)
            """)
    List<Long> findMemberIdsAssignedOn(@Param("employerId") Long employerId, @Param("date") LocalDate date);

    /**
     * Every member EVER assigned to this employer, at any date.
     *
     * The question an audit filter asks, and a different one from
     * findMemberIdsAssignedOn. An audit trail for an employer that showed only
     * the people currently with them would omit everything about the ones who
     * left -- which is most of what an audit is opened to find.
     */
    @Query("SELECT DISTINCT a.memberId FROM MemberEmployerAssignment a WHERE a.employerId = :employerId")
    List<Long> findMemberIdsEverAssignedTo(@Param("employerId") Long employerId);

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

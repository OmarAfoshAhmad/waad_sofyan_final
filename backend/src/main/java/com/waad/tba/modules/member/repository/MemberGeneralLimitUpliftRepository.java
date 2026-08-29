package com.waad.tba.modules.member.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.member.entity.MemberGeneralLimitUplift;

@Repository
public interface MemberGeneralLimitUpliftRepository extends JpaRepository<MemberGeneralLimitUplift, Long> {

    /**
     * The total uplift in force for each of these members on this date, one
     * query for the whole page.
     *
     * Per-member would put a query on every row of the members list, which is
     * the cost MemberLimitOverviewService exists to avoid; an uplift is rare
     * enough that most pages get an empty result and pay for one query.
     *
     * Members with no uplift are absent from the result rather than present
     * with zero -- the caller defaults, and an absent row and a zero row mean
     * the same thing here.
     */
    @Query("""
            select u.memberId, sum(u.amount) from MemberGeneralLimitUplift u
            where u.memberId in :memberIds
              and u.effectiveFrom <= :asOfDate
              and (u.effectiveTo is null or u.effectiveTo > :asOfDate)
            group by u.memberId
            """)
    List<Object[]> sumInForceByMember(@Param("memberIds") Collection<Long> memberIds,
            @Param("asOfDate") LocalDate asOfDate);

    /** The same figure for one member, for the single-member ceiling read. */
    @Query("""
            select coalesce(sum(u.amount), 0) from MemberGeneralLimitUplift u
            where u.memberId = :memberId
              and u.effectiveFrom <= :asOfDate
              and (u.effectiveTo is null or u.effectiveTo > :asOfDate)
            """)
    BigDecimal sumInForceFor(@Param("memberId") Long memberId, @Param("asOfDate") LocalDate asOfDate);

    /**
     * The row, locked for the duration of the transaction that ends it.
     *
     * Revoking is read-check-write: load it, refuse if it is already revoked,
     * close the window. Two administrators clicking at the same moment would
     * both read a live uplift, both pass the check, and both write -- the
     * second overwriting the first's reason and the name against it, with the
     * refusal that should have happened never happening. The lock makes the
     * second wait and then find what it is meant to find.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from MemberGeneralLimitUplift u where u.id = :id")
    java.util.Optional<MemberGeneralLimitUplift> findByIdForRevocation(@Param("id") Long id);

    /**
     * Everything ever granted to this member, newest first -- expired and
     * revoked included. The history is the point: an exception whose record
     * disappears when it ends cannot be audited afterwards.
     */
    List<MemberGeneralLimitUplift> findByMemberIdOrderByEffectiveFromDescIdDesc(Long memberId);
}

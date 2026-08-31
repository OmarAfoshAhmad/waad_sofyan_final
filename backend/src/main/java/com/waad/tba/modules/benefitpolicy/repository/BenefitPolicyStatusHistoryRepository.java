package com.waad.tba.modules.benefitpolicy.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyStatusHistory;

@Repository
public interface BenefitPolicyStatusHistoryRepository extends JpaRepository<BenefitPolicyStatusHistory, Long> {

    /** The interval covering serviceDate for one policy -- half-open [validFrom, validTo). */
    @Query("select h from BenefitPolicyStatusHistory h where h.policyId = :policyId"
            + " and h.validFrom <= :date and (h.validTo is null or h.validTo > :date)")
    Optional<BenefitPolicyStatusHistory> findCovering(
            @Param("policyId") Long policyId, @Param("date") LocalDate date);

    /**
     * The same lookup for many policies at once, at the SAME date -- the
     * shape {@code MemberPolicyResolver.resolveForMembers} needs to avoid
     * one query per policy in a batch resolution.
     */
    @Query("select h from BenefitPolicyStatusHistory h where h.policyId in :policyIds"
            + " and h.validFrom <= :date and (h.validTo is null or h.validTo > :date)")
    List<BenefitPolicyStatusHistory> findCoveringForPolicies(
            @Param("policyIds") Collection<Long> policyIds, @Param("date") LocalDate date);

    /** The currently open interval -- the one a new transition must close. */
    Optional<BenefitPolicyStatusHistory> findByPolicyIdAndValidToIsNull(Long policyId);

    /**
     * Whether this policy has EVER had a status transition recorded here.
     * A policy created outside {@code BenefitPolicyService} (a fixture, a
     * legacy row predating V210's backfill window in a database this
     * migration never ran against, a direct repository write) has none --
     * for exactly that case, {@code MemberPolicyResolver} falls back to
     * the current status column, the same honest approximation V210's own
     * backfill uses for pre-existing rows, rather than refusing every
     * historical read for a policy this table was never told about.
     */
    boolean existsByPolicyId(Long policyId);

    /** The bulk form of {@link #existsByPolicyId}, for one batch resolution. */
    @Query("select h.policyId from BenefitPolicyStatusHistory h where h.policyId in :policyIds group by h.policyId")
    List<Long> findPolicyIdsWithAnyHistory(@Param("policyIds") Collection<Long> policyIds);
}

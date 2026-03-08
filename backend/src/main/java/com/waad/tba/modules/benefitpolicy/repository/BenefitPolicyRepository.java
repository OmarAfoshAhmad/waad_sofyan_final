package com.waad.tba.modules.benefitpolicy.repository;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for BenefitPolicy entity.
 */
@Repository
public interface BenefitPolicyRepository extends JpaRepository<BenefitPolicy, Long> {

    // ═══════════════════════════════════════════════════════════════════════════
    // BASIC QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find by policy code
     */
    Optional<BenefitPolicy> findByPolicyCode(String policyCode);

    /**
     * Find all active (not soft-deleted) policies
     */
    List<BenefitPolicy> findByActiveTrue();

    /**
     * Find all active (not soft-deleted) policies - paginated
     */
    Page<BenefitPolicy> findByActiveTrue(Pageable pageable);

    /**
     * Find soft-deleted policies - paginated
     */
    Page<BenefitPolicy> findByActiveFalse(Pageable pageable);

    /**
     * Find all policies with a specific status
     */
    List<BenefitPolicy> findByStatus(BenefitPolicyStatus status);

    /**
     * Find all active policies with a specific status
     */
    List<BenefitPolicy> findByStatusAndActiveTrue(BenefitPolicyStatus status);

    // ═══════════════════════════════════════════════════════════════════════════
    // EMPLOYER QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find all policies for an employer
     */
    List<BenefitPolicy> findByEmployerIdAndActiveTrue(Long employerOrgId);

    /**
     * Find policies for an employer with a specific status
     */
    List<BenefitPolicy> findByEmployerIdAndStatusAndActiveTrue(
            Long employerOrgId, BenefitPolicyStatus status);

    /**
     * Find paginated policies for an employer
     */
    Page<BenefitPolicy> findByEmployerIdAndActiveTrue(Long employerOrgId, Pageable pageable);

    // ═══════════════════════════════════════════════════════════════════════════
    // DATE-BASED QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find policies effective on a specific date for an employer
     */
    @Query("SELECT bp FROM BenefitPolicy bp " +
           "WHERE bp.employer.id = :employerOrgId " +
           "AND bp.status = 'ACTIVE' " +
           "AND bp.active = true " +
           "AND bp.startDate <= :date " +
           "AND bp.endDate >= :date")
    List<BenefitPolicy> findEffectivePoliciesForEmployer(
            @Param("employerOrgId") Long employerOrgId,
            @Param("date") LocalDate date);

    /**
     * Find active policy for employer on a specific date (should return 0 or 1)
     */
    @Query("SELECT bp FROM BenefitPolicy bp " +
           "WHERE bp.employer.id = :employerOrgId " +
           "AND bp.status = 'ACTIVE' " +
           "AND bp.active = true " +
           "AND bp.startDate <= :date " +
           "AND bp.endDate >= :date " +
           "ORDER BY bp.createdAt DESC")
    Optional<BenefitPolicy> findActiveEffectivePolicyForEmployer(
            @Param("employerOrgId") Long employerOrgId,
            @Param("date") LocalDate date);

    /**
     * Check if there's an overlapping active policy for the employer
     */
    @Query("SELECT COUNT(bp) > 0 FROM BenefitPolicy bp " +
           "WHERE bp.employer.id = :employerOrgId " +
           "AND bp.status = 'ACTIVE' " +
           "AND bp.active = true " +
           "AND bp.id != :excludeId " +
           "AND bp.startDate <= :endDate " +
           "AND bp.endDate >= :startDate")
    boolean existsOverlappingActivePolicy(
            @Param("employerOrgId") Long employerOrgId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId);

    /**
     * Check if there's an overlapping active policy (for new policies)
     */
    @Query("SELECT COUNT(bp) > 0 FROM BenefitPolicy bp " +
           "WHERE bp.employer.id = :employerOrgId " +
           "AND bp.status = 'ACTIVE' " +
           "AND bp.active = true " +
           "AND bp.startDate <= :endDate " +
           "AND bp.endDate >= :startDate")
    boolean existsOverlappingActivePolicyNew(
            @Param("employerOrgId") Long employerOrgId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH & FILTER QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Search policies by name
     */
    @Query("SELECT bp FROM BenefitPolicy bp " +
           "WHERE bp.active = true " +
           "AND (LOWER(bp.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(bp.policyCode) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<BenefitPolicy> searchByNameOrCode(@Param("search") String search, Pageable pageable);

    /**
     * Find policies expiring soon (within N days)
     */
    @Query("SELECT bp FROM BenefitPolicy bp " +
           "WHERE bp.status = 'ACTIVE' " +
           "AND bp.active = true " +
           "AND bp.endDate BETWEEN :today AND :futureDate")
    List<BenefitPolicy> findPoliciesExpiringSoon(
            @Param("today") LocalDate today,
            @Param("futureDate") LocalDate futureDate);

    /**
     * Find expired policies that need status update
     */
    @Query("SELECT bp FROM BenefitPolicy bp " +
           "WHERE bp.status = 'ACTIVE' " +
           "AND bp.active = true " +
           "AND bp.endDate < :today")
    List<BenefitPolicy> findExpiredActivePolicies(@Param("today") LocalDate today);

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICS QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Count policies by status
     */
    long countByStatusAndActiveTrue(BenefitPolicyStatus status);

    /**
     * Count policies for an employer
     */
    long countByEmployerIdAndActiveTrue(Long employerOrgId);

    /**
     * Find the highest policy code for a given year prefix (for auto-code generation)
     * Example: For prefix "POL-2025-", returns "POL-2025-005" if that's the highest
     */
    @Query(value = "SELECT bp.policy_code FROM benefit_policies bp " +
           "WHERE bp.policy_code LIKE CONCAT(:yearPrefix, '%') " +
           "ORDER BY bp.policy_code DESC " +
           "LIMIT 1",
           nativeQuery = true)
    Optional<String> findMaxPolicyCodeByYearPrefix(@Param("yearPrefix") String yearPrefix);
}


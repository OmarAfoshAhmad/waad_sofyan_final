package com.waad.tba.modules.eligibility.repository;

import com.waad.tba.modules.eligibility.entity.EligibilityCheck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Eligibility Check Repository
 * Phase E1 - Eligibility Engine
 * 
 * Repository for eligibility check audit records.
 * Read-only operations for audit trail viewing.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Repository
public interface EligibilityCheckRepository extends JpaRepository<EligibilityCheck, Long> {

       // ================================================
       // By Request ID
       // ================================================

       /**
        * Find by unique request ID
        */
       Optional<EligibilityCheck> findByRequestId(String requestId);

       // ================================================
       // By Member
       // ================================================

       /**
        * Find all checks for a member
        */
       Page<EligibilityCheck> findByMemberId(Long memberId, Pageable pageable);

       /**
        * Find all checks for a member within the given company scope (security:
        * prevents cross-employer data access)
        */
       Page<EligibilityCheck> findByMemberIdAndCompanyScopeId(Long memberId, Long companyScopeId, Pageable pageable);

       /**
        * Find checks for a member within date range
        */
       @Query("SELECT e FROM EligibilityCheck e WHERE e.memberId = :memberId " +
                     "AND e.checkTimestamp BETWEEN :startDate AND :endDate ORDER BY e.checkTimestamp DESC")
       List<EligibilityCheck> findByMemberIdAndDateRange(
                     @Param("memberId") Long memberId,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       // ================================================
       // By Policy
       // ================================================

       /**
        * Find all checks for a policy
        */
       Page<EligibilityCheck> findByPolicyId(Long policyId, Pageable pageable);

       /**
        * Find all checks for a policy within the given company scope (security:
        * prevents cross-employer data access)
        */
       Page<EligibilityCheck> findByPolicyIdAndCompanyScopeId(Long policyId, Long companyScopeId, Pageable pageable);

       // ================================================
       // By Company Scope
       // ================================================

       /**
        * Find all checks for a company
        */
       Page<EligibilityCheck> findByCompanyScopeId(Long companyScopeId, Pageable pageable);

       /**
        * Find checks for company within date range
        */
       @Query("SELECT e FROM EligibilityCheck e WHERE e.companyScopeId = :companyScopeId " +
                     "AND e.checkTimestamp BETWEEN :startDate AND :endDate ORDER BY e.checkTimestamp DESC")
       Page<EligibilityCheck> findByCompanyScopeIdAndDateRange(
                     @Param("companyScopeId") Long companyScopeId,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     Pageable pageable);

       // ================================================
       // By User
       // ================================================

       /**
        * Find all checks performed by a user
        */
       Page<EligibilityCheck> findByCheckedByUserId(Long userId, Pageable pageable);

       // ================================================
       // By Result
       // ================================================

       /**
        * Find checks by eligibility result
        */
       Page<EligibilityCheck> findByEligible(Boolean eligible, Pageable pageable);

       /**
        * Find failed checks for company
        */
       @Query("SELECT e FROM EligibilityCheck e WHERE e.companyScopeId = :companyScopeId " +
                     "AND e.eligible = false AND e.checkTimestamp >= :since ORDER BY e.checkTimestamp DESC")
       List<EligibilityCheck> findFailedChecksForCompanySince(
                     @Param("companyScopeId") Long companyScopeId,
                     @Param("since") LocalDateTime since);

       // ================================================
       // Statistics
       // ================================================

       /**
        * Count checks for member
        */
       long countByMemberId(Long memberId);

       /**
        * Count checks for policy
        */
       long countByPolicyId(Long policyId);

       /**
        * Count checks for company today
        */
       @Query("SELECT COUNT(e) FROM EligibilityCheck e WHERE e.companyScopeId = :companyScopeId " +
                     "AND e.checkTimestamp >= :todayStart")
       long countChecksForCompanyToday(
                     @Param("companyScopeId") Long companyScopeId,
                     @Param("todayStart") LocalDateTime todayStart);

       /**
        * Count failed checks for company today
        */
       @Query("SELECT COUNT(e) FROM EligibilityCheck e WHERE e.companyScopeId = :companyScopeId " +
                     "AND e.eligible = false AND e.checkTimestamp >= :todayStart")
       long countFailedChecksForCompanyToday(
                     @Param("companyScopeId") Long companyScopeId,
                     @Param("todayStart") LocalDateTime todayStart);

       // ================================================
       // Search
       // ================================================

       /**
        * Search by member name or civil ID
        */
       @Query("SELECT e FROM EligibilityCheck e WHERE " +
                     "(e.companyScopeId = :companyScopeId OR :companyScopeId IS NULL) AND " +
                     "(LOWER(e.memberName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                     "e.memberCivilId LIKE CONCAT('%', :search, '%') OR " +
                     "e.requestId LIKE CONCAT('%', :search, '%')) " +
                     "ORDER BY e.checkTimestamp DESC")
       Page<EligibilityCheck> search(
                     @Param("companyScopeId") Long companyScopeId,
                     @Param("search") String search,
                     Pageable pageable);

       /**
        * Find recent checks for dashboard
        */
       @Query("SELECT e FROM EligibilityCheck e WHERE " +
                     "(e.companyScopeId = :companyScopeId OR :companyScopeId IS NULL) " +
                     "ORDER BY e.checkTimestamp DESC")
       List<EligibilityCheck> findRecentChecks(
                     @Param("companyScopeId") Long companyScopeId,
                     Pageable pageable);
}

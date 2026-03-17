package com.waad.tba.modules.claim.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.projection.MonthlyTrendProjection;
import com.waad.tba.modules.claim.projection.CostsByProviderProjection;
import com.waad.tba.modules.claim.projection.ServiceDistributionProjection;
import com.waad.tba.modules.claim.projection.RecentClaimProjection;
import com.waad.tba.modules.claim.projection.FinancialSummaryByProviderProjection;
import com.waad.tba.modules.claim.projection.FinancialSummaryByStatusProjection;
import com.waad.tba.modules.claim.projection.FinancialSummaryByEmployerProjection;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

        // ═══════════════════════════════════════════════════════════════════════════════
        // FINANCIAL CLOSURE: PESSIMISTIC LOCKING FOR ALL FINANCIAL OPERATIONS
        // ═══════════════════════════════════════════════════════════════════════════════
        // Use SELECT ... FOR UPDATE to prevent double settlement, double approval,
        // and race conditions. This is MANDATORY for financial integrity.
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Find claim by ID with pessimistic write lock (SELECT ... FOR UPDATE).
         * MANDATORY for ALL financial state changes: approve, reject, settle.
         * 
         * @param id Claim ID
         * @return Claim with exclusive lock held until transaction commits
         */
        @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT c FROM Claim c WHERE c.id = :id")
        java.util.Optional<Claim> findByIdForUpdate(@Param("id") Long id);

        /**
         * Find multiple claims by IDs with pessimistic write lock (SELECT ... FOR
         * UPDATE).
         * MANDATORY for batch payment processing to prevent concurrent settlement.
         *
         * @param ids List of claim IDs
         * @return Claims with exclusive locks held until transaction commits
         */
        @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT c FROM Claim c WHERE c.id IN :ids")
        java.util.List<Claim> findAllByIdWithLock(@Param("ids") java.util.List<Long> ids);

        /**
         * Find claim by ID with pessimistic write lock AND full fetch joins.
         * MANDATORY for approval operations that need member and benefit policy data.
         * Prevents N+1 queries while maintaining financial locking.
         * 
         * @param id Claim ID
         * @return Claim with exclusive lock and eagerly loaded relationships
         */
        @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "LEFT JOIN FETCH m.benefitPolicy bp " +
                        "LEFT JOIN FETCH c.preAuthorization pa " +
                        "LEFT JOIN FETCH c.lines cl " +
                        "WHERE c.id = :id")
        java.util.Optional<Claim> findByIdForFinancialUpdate(@Param("id") Long id);

        /**
         * PHASE 5.B: Enhanced with full fetch joins for member.benefitPolicy
         * to avoid N+1 queries in ClaimMapper.toViewDto()
         * FIXED: Added countQuery for proper pagination with FETCH JOIN
         */
        @EntityGraph(attributePaths = { "member", "member.benefitPolicy", "member.employer", "preAuthorization",
                        "visit" })
        @Query(value = "SELECT c FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(c.member.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(c.member.civilId) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))", countQuery = "SELECT COUNT(c) FROM Claim c WHERE c.active = true AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) OR LOWER(c.member.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) OR LOWER(c.member.civilId) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))")
        Page<Claim> searchPaged(@Param("keyword") String keyword, Pageable pageable);

        /**
         * Search claims with pagination filtered by employer ID.
         * PHASE 5.B: Enhanced with full fetch joins
         * FIXED: Added countQuery for proper pagination with FETCH JOIN
         */
        @EntityGraph(attributePaths = { "member", "member.benefitPolicy", "member.employer", "preAuthorization",
                        "visit" })
        @Query(value = "SELECT c FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND (:employerId IS NULL OR c.member.employer.id = :employerId) " +
                        "AND (:providerId IS NULL OR c.providerId = :providerId) " +
                        "AND (:status IS NULL OR c.status = :status) " +
                        "AND (CAST(:dateFrom AS date) IS NULL OR c.serviceDate >= :dateFrom) " +
                        "AND (CAST(:dateTo AS date) IS NULL OR c.serviceDate <= :dateTo) " +
                        "AND (CAST(:createdAtFrom AS timestamp) IS NULL OR c.createdAt >= :createdAtFrom) " +
                        "AND (CAST(:createdAtTo AS timestamp) IS NULL OR c.createdAt < :createdAtTo) " +
                        "AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(c.member.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(c.member.civilId) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))", countQuery = "SELECT COUNT(c) FROM Claim c WHERE c.active = true AND (:employerId IS NULL OR c.member.employer.id = :employerId) AND (:providerId IS NULL OR c.providerId = :providerId) AND (:status IS NULL OR c.status = :status) AND (CAST(:dateFrom AS date) IS NULL OR c.serviceDate >= :dateFrom) AND (CAST(:dateTo AS date) IS NULL OR c.serviceDate <= :dateTo) AND (CAST(:createdAtFrom AS timestamp) IS NULL OR c.createdAt >= :createdAtFrom) AND (CAST(:createdAtTo AS timestamp) IS NULL OR c.createdAt < :createdAtTo) AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) OR LOWER(c.member.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) OR LOWER(c.member.civilId) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))")
        Page<Claim> searchPagedWithFilters(
                        @Param("keyword") String keyword,
                        @Param("employerId") Long employerId,
                        @Param("providerId") Long providerId,
                        @Param("status") com.waad.tba.modules.claim.entity.ClaimStatus status,
                        @Param("dateFrom") java.time.LocalDate dateFrom,
                        @Param("dateTo") java.time.LocalDate dateTo,
                        @Param("createdAtFrom") java.time.LocalDateTime createdAtFrom,
                        @Param("createdAtTo") java.time.LocalDateTime createdAtTo,
                        Pageable pageable);

        /**
         * Search claims with pagination filtered by provider ID.
         * PHASE 6: Added for Provider Portal
         * FIXED: Changed m.employer to m.employer (canonical field name)
         */
        @EntityGraph(attributePaths = { "member", "member.benefitPolicy", "member.employer", "preAuthorization",
                        "visit" })
        @Query(value = "SELECT c FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.providerId = :providerId " +
                        "AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(c.member.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(c.member.civilId) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))", countQuery = "SELECT COUNT(c) FROM Claim c WHERE c.active = true AND c.providerId = :providerId AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) OR LOWER(c.member.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) OR LOWER(c.member.civilId) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))")
        Page<Claim> searchPagedByProviderId(@Param("keyword") String keyword, @Param("providerId") Long providerId,
                        Pageable pageable);

        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "WHERE c.active = true " +
                        "AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', :query, '%')))")
        List<Claim> search(@Param("query") String query);

        /**
         * Search claims (non-paginated) filtered by employer ID.
         */
        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "WHERE c.active = true " +
                        "AND m.employer.id = :employerId " +
                        "AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', :query, '%')))")
        List<Claim> searchByEmployerId(@Param("query") String query, @Param("employerId") Long employerId);

        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member " +
                        "LEFT JOIN FETCH c.preAuthorization " +
                        "WHERE c.member.id = :memberId AND c.active = true")
        List<Claim> findByMemberId(@Param("memberId") Long memberId);

        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member " +
                        "LEFT JOIN FETCH c.preAuthorization " +
                        "WHERE c.preAuthorization.id = :preAuthorizationId AND c.active = true")
        List<Claim> findByPreAuthorizationId(@Param("preAuthorizationId") Long preAuthorizationId);

        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true")
        long countActive();

        /**
         * Count claims filtered by employer ID.
         */
        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true AND c.member.employer.id = :employerId")
        long countByMemberEmployerId(@Param("employerId") Long employerId);

        /**
         * Find claims by member ID and status list.
         * Used for deductible and out-of-pocket calculations.
         * 
         * @param memberId The member ID
         * @param statuses List of claim statuses to include
         * @return List of matching claims
         */
        @Query("SELECT c FROM Claim c " +
                        "WHERE c.member.id = :memberId " +
                        "AND c.status IN :statuses " +
                        "AND c.active = true")
        List<Claim> findByMemberIdAndStatusIn(@Param("memberId") Long memberId,
                        @Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses);

        // ═══════════════════════════════════════════════════════════════════════════════
        // MVP PHASE: Inbox Queries
        // PHASE 5.B: Enhanced with full fetch joins to avoid N+1 in DTO mapping
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Find claims by status list with pagination (for inbox views).
         * PHASE 5.B: Full fetch joins for member, benefitPolicy
         * FIXED: Added countQuery to fix "Streamable to Page" conversion error
         */
        @EntityGraph(attributePaths = { "member", "member.benefitPolicy", "preAuthorization", "visit" })
        @Query(value = "SELECT c FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.status IN :statuses", countQuery = "SELECT COUNT(c) FROM Claim c WHERE c.active = true AND c.status IN :statuses")
        Page<Claim> findByStatusIn(@Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses,
                        Pageable pageable);

        /**
         * Find claims by single status with pagination.
         * PHASE 5.B: Full fetch joins for member, benefitPolicy
         * FIXED: Added countQuery to fix "Streamable to Page" conversion error
         */
        @EntityGraph(attributePaths = { "member", "member.benefitPolicy", "preAuthorization", "visit" })
        @Query(value = "SELECT c FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.status = :status", countQuery = "SELECT COUNT(c) FROM Claim c WHERE c.active = true AND c.status = :status")
        Page<Claim> findByStatus(@Param("status") com.waad.tba.modules.claim.entity.ClaimStatus status,
                        Pageable pageable);

        /**
         * Count claims by status.
         */
        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true AND c.status = :status")
        long countByStatus(@Param("status") com.waad.tba.modules.claim.entity.ClaimStatus status);

        /**
         * Count claims by status list.
         */
        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true AND c.status IN :statuses")
        long countByStatusIn(@Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses);

        // ═══════════════════════════════════════════════════════════════════════════════
        // PROVIDER FILTERING METHODS
        // ADDED 2026-01-05: For PROVIDER role to see only their own claims
        // Global Best Practice: Healthcare providers should only access their claims
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Search claims (non-paginated) filtered by provider ID.
         * Used by PROVIDER role to search within their own claims.
         */
        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "LEFT JOIN FETCH m.benefitPolicy bp " +
                        " " +
                        "WHERE c.active = true " +
                        "AND c.providerId = :providerId " +
                        "AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(m.civilId) LIKE LOWER(CONCAT('%', :query, '%')))")
        List<Claim> searchByProviderId(@Param("query") String query, @Param("providerId") Long providerId);

        /**
         * Find all claims for a specific provider (non-paginated).
         * Used by PROVIDER role to view all their claims.
         */
        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "LEFT JOIN FETCH m.benefitPolicy bp " +
                        " " +
                        "WHERE c.active = true " +
                        "AND c.providerId = :providerId")
        List<Claim> findByProviderId(@Param("providerId") Long providerId);

        /**
         * Find all active claims for a provider (for documents aggregation)
         */
        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "WHERE c.active = true AND c.providerId = :providerId " +
                        "ORDER BY c.serviceDate DESC")
        List<Claim> findByProviderIdAndActiveTrue(@Param("providerId") Long providerId);

        /**
         * Find claims for settlement report with optimized filtering at DB level.
         * PERFORMANCE CRITICAL: All filtering done in database, not in memory.
         * 
         * @param providerId Provider ID (required)
         * @param statuses   List of claim statuses to include
         * @param fromDate   Service date from (inclusive)
         * @param toDate     Service date to (inclusive)
         * @return List of claims with member and preAuth eagerly loaded
         */
        @Query("SELECT DISTINCT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "LEFT JOIN FETCH m.benefitPolicy bp " +
                        "LEFT JOIN FETCH c.preAuthorization pa " +
                        "LEFT JOIN FETCH c.lines cl " +
                        "WHERE c.active = true " +
                        "AND c.providerId = :providerId " +
                        "AND (:employerOrgId IS NULL OR c.member.employer.id = :employerOrgId) " +
                        "AND c.status IN :statuses " +
                        "AND c.serviceDate >= :fromDate " +
                        "AND c.serviceDate <= :toDate " +
                        "ORDER BY c.serviceDate ASC")
        List<Claim> findForSettlementReport(
                        @Param("providerId") Long providerId,
                        @Param("employerOrgId") Long employerOrgId,
                        @Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses,
                        @Param("fromDate") java.time.LocalDate fromDate,
                        @Param("toDate") java.time.LocalDate toDate);

        /**
         * Count claims for settlement report (for validation/statistics).
         */
        @Query("SELECT COUNT(c) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.providerId = :providerId " +
                        "AND c.status IN :statuses " +
                        "AND c.serviceDate >= :fromDate " +
                        "AND c.serviceDate <= :toDate")
        long countForSettlementReport(
                        @Param("providerId") Long providerId,
                        @Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses,
                        @Param("fromDate") java.time.LocalDate fromDate,
                        @Param("toDate") java.time.LocalDate toDate);

        /**
         * Get settlement totals directly from database (NO entity loading).
         * CANONICAL: All financial calculations in database for accuracy.
         * 
         * Returns: [totalRequested, totalApproved, totalCoPay, count]
         */
        @Query("SELECT " +
                        "COALESCE(SUM(c.requestedAmount), 0), " +
                        "COALESCE(SUM(c.approvedAmount), 0), " +
                        "COALESCE(SUM(c.patientCoPay), 0), " +
                        "COUNT(c) " +
                        "FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.providerId = :providerId " +
                        "AND (:employerOrgId IS NULL OR c.member.employer.id = :employerOrgId) " +
                        "AND c.status IN :statuses " +
                        "AND c.serviceDate >= :fromDate " +
                        "AND c.serviceDate <= :toDate")
        List<Object[]> getSettlementTotals(
                        @Param("providerId") Long providerId,
                        @Param("employerOrgId") Long employerOrgId,
                        @Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses,
                        @Param("fromDate") java.time.LocalDate fromDate,
                        @Param("toDate") java.time.LocalDate toDate);

        /**
         * Count claims by provider ID.
         */
        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true AND c.providerId = :providerId")
        long countByProviderId(@Param("providerId") Long providerId);

        /**
         * Find claims by provider ID and status with pagination.
         * Used by PROVIDER role to filter their claims by status.
         * FIXED: Added countQuery for proper pagination with FETCH JOIN
         */
        @EntityGraph(attributePaths = { "member", "member.benefitPolicy", "preAuthorization", "visit" })
        @Query(value = "SELECT c FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.providerId = :providerId " +
                        "AND c.status = :status", countQuery = "SELECT COUNT(c) FROM Claim c WHERE c.active = true AND c.providerId = :providerId AND c.status = :status")
        Page<Claim> findByProviderIdAndStatus(@Param("providerId") Long providerId,
                        @Param("status") com.waad.tba.modules.claim.entity.ClaimStatus status,
                        Pageable pageable);

        /**
         * Get claim summaries for visits (bulk processing).
         * Returns: [visitId, claimCount, latestClaimId, latestClaimStatus]
         */
        @Query("SELECT c.visit.id, COUNT(c), MAX(c.id) FROM Claim c " +
                        "WHERE c.visit.id IN :visitIds AND c.active = true " +
                        "GROUP BY c.visit.id")
        List<Object[]> findClaimSummariesByVisitIds(@Param("visitIds") java.util.Collection<Long> visitIds);

        /**
         * Find claims by multiple IDs for latest status resolution
         */
        @Query("SELECT c FROM Claim c WHERE c.id IN :ids")
        List<Claim> findSummaryBaseByIds(@Param("ids") java.util.Collection<Long> ids);

        // ═══════════════════════════════════════════════════════════════════════════════
        // DASHBOARD STATISTICS QUERIES (Phase A)
        // Aggregations using JPQL - No Lazy Loading, No Entities returned
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Count claims by status (aggregation)
         */
        @Query("SELECT c.status, COUNT(c) FROM Claim c WHERE c.active = true GROUP BY c.status")
        List<Object[]> countByStatusGrouped();

        /**
         * Count open claims (SUBMITTED, UNDER_REVIEW)
         */
        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.SUBMITTED, " +
                        "com.waad.tba.modules.claim.entity.ClaimStatus.UNDER_REVIEW)")
        long countOpenClaims();

        /**
         * Count approved claims (APPROVED, SETTLED)
         */
        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, " +
                        "com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED)")
        long countApprovedClaims();

        /**
         * Sum total approved amounts
         */
        @Query("SELECT COALESCE(SUM(c.approvedAmount), 0) FROM Claim c " +
                        "WHERE c.active = true AND c.approvedAmount IS NOT NULL")
        java.math.BigDecimal sumApprovedAmounts();

        /**
         * Get monthly trends (claims count per month)
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT YEAR(c.createdAt) as year, MONTH(c.createdAt) as month, COUNT(c) as count " +
                        "FROM Claim c WHERE c.active = true " +
                        "AND c.createdAt >= :startDate " +
                        "AND c.createdAt <= :endDate " +
                        "GROUP BY YEAR(c.createdAt), MONTH(c.createdAt) " +
                        "ORDER BY year, month")
        List<MonthlyTrendProjection> getMonthlyTrends(@Param("startDate") java.time.LocalDateTime startDate,
                        @Param("endDate") java.time.LocalDateTime endDate);

        /**
         * Get costs by provider (aggregated)
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT c.providerId as providerId, c.providerName as providerName, " +
                        "COALESCE(SUM(c.approvedAmount), 0) as totalCost, " +
                        "COUNT(c) as claimCount " +
                        "FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.approvedAmount IS NOT NULL " +
                        "GROUP BY c.providerId, c.providerName " +
                        "ORDER BY totalCost DESC")
        List<CostsByProviderProjection> getCostsByProvider();

        /**
         * Get costs by provider filtered by employer
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT c.providerId as providerId, c.providerName as providerName, " +
                        "COALESCE(SUM(c.approvedAmount), 0) as totalCost, " +
                        "COUNT(c) as claimCount " +
                        "FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "AND c.approvedAmount IS NOT NULL " +
                        "GROUP BY c.providerId, c.providerName " +
                        "ORDER BY totalCost DESC")
        List<CostsByProviderProjection> getCostsByProviderByEmployer(@Param("employerOrgId") Long employerOrgId);

        /**
         * Get service distribution (aggregated by medical service category)
         * Uses ClaimLines to determine actual service usage.
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT COALESCE(l.serviceName, 'خدمة غير محددة') as serviceName, " +
                        "COUNT(DISTINCT c.id) as count " +
                        "FROM Claim c JOIN c.lines l " +
                        "WHERE c.active = true " +
                        "GROUP BY l.serviceName " +
                        "ORDER BY count DESC")
        List<ServiceDistributionProjection> getServiceDistribution();

        /**
         * Get service distribution filtered by employer
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT COALESCE(l.serviceName, 'خدمة غير محددة') as serviceName, " +
                        "COUNT(DISTINCT c.id) as count " +
                        "FROM Claim c JOIN c.lines l " +
                        "WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "GROUP BY l.serviceName " +
                        "ORDER BY count DESC")
        List<ServiceDistributionProjection> getServiceDistributionByEmployer(
                        @Param("employerOrgId") Long employerOrgId);

        /**
         * Get recent claims (for dashboard recent activities)
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT c.id as id, " +
                        "c.member.fullName as memberName, " +
                        "c.diagnosisDescription as diagnosisDescription, " +
                        "c.status as status, " +
                        "c.createdAt as createdAt " +
                        "FROM Claim c " +
                        "WHERE c.active = true " +
                        "ORDER BY c.createdAt DESC")
        List<RecentClaimProjection> getRecentClaims(Pageable pageable);

        /**
         * Count claims created in date range (for growth calculation)
         */
        @Query("SELECT COUNT(c) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.createdAt >= :startDate " +
                        "AND c.createdAt < :endDate")
        long countClaimsInDateRange(@Param("startDate") java.time.LocalDateTime startDate,
                        @Param("endDate") java.time.LocalDateTime endDate);

        // ═══════════════════════════════════════════════════════════════════════════════
        // EMPLOYER-FILTERED DASHBOARD QUERIES (Phase A)
        // Used when dashboard is filtered by specific employer organization
        // Note: countByMemberEmployerId is already defined above in line 171
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Count open claims (SUBMITTED, UNDER_REVIEW) by employer organization
         */
        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.SUBMITTED, " +
                        "com.waad.tba.modules.claim.entity.ClaimStatus.UNDER_REVIEW)")
        long countOpenClaimsByEmployer(@Param("employerOrgId") Long employerOrgId);

        /**
         * Count approved claims (APPROVED, SETTLED) by employer organization
         */
        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, " +
                        "com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED)")
        long countApprovedClaimsByEmployer(@Param("employerOrgId") Long employerOrgId);

        /**
         * Sum total approved amounts by employer organization
         */
        @Query("SELECT COALESCE(SUM(c.approvedAmount), 0) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "AND c.approvedAmount IS NOT NULL")
        java.math.BigDecimal sumApprovedAmountsByEmployer(@Param("employerOrgId") Long employerOrgId);

        /**
         * Get monthly trends (claims count per month) filtered by employer organization
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT YEAR(c.createdAt) as year, MONTH(c.createdAt) as month, COUNT(c) as count " +
                        "FROM Claim c WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "AND c.createdAt >= :startDate " +
                        "AND c.createdAt <= :endDate " +
                        "GROUP BY YEAR(c.createdAt), MONTH(c.createdAt) " +
                        "ORDER BY year, month")
        List<MonthlyTrendProjection> getMonthlyTrendsByEmployer(@Param("startDate") java.time.LocalDateTime startDate,
                        @Param("endDate") java.time.LocalDateTime endDate,
                        @Param("employerOrgId") Long employerOrgId);

        /**
         * Count claims created in date range by employer organization (for growth
         * calculation)
         */
        @Query("SELECT COUNT(c) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "AND c.createdAt >= :startDate " +
                        "AND c.createdAt < :endDate")
        long countClaimsInDateRangeByEmployer(@Param("startDate") java.time.LocalDateTime startDate,
                        @Param("endDate") java.time.LocalDateTime endDate,
                        @Param("employerOrgId") Long employerOrgId);

        // ═══════════════════════════════════════════════════════════════════════════
        // PHASE 1: SLA TRACKING QUERIES
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Find claims that exceeded SLA (businessDaysTaken > slaDaysConfigured).
         * Used for SLA compliance reporting and alerts.
         */
        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "WHERE c.active = true " +
                        "AND c.withinSla = false " +
                        "AND c.businessDaysTaken IS NOT NULL")
        List<Claim> findClaimsExceededSla();

        /**
         * Find claims approaching deadline (expected completion within N days).
         * Used for daily SLA monitoring and reviewer alerts.
         * 
         * @param fromDate Start of date range (typically today)
         * @param toDate   End of date range (typically today + 2 days)
         * @return Claims in UNDER_REVIEW status with deadline approaching
         */
        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "WHERE c.active = true " +
                        "AND c.status = 'UNDER_REVIEW' " +
                        "AND c.expectedCompletionDate BETWEEN :fromDate AND :toDate")
        List<Claim> findClaimsApproachingDeadline(@Param("fromDate") java.time.LocalDate fromDate,
                        @Param("toDate") java.time.LocalDate toDate);

        /**
         * Calculate average processing time (business days) for completed claims.
         * Used for SLA metrics dashboard.
         */
        @Query("SELECT AVG(c.businessDaysTaken) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.businessDaysTaken IS NOT NULL")
        Double getAverageProcessingDays();

        /**
         * Calculate SLA compliance rate (percentage of claims completed within SLA).
         * Returns value between 0-100.
         */
        @Query("SELECT CAST(COUNT(CASE WHEN c.withinSla = true THEN 1 END) AS double) * 100.0 / " +
                        "CAST(COUNT(c) AS double) " +
                        "FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.withinSla IS NOT NULL")
        Double getSlaComplianceRate();

        /**
         * Count claims by SLA status.
         * Returns: [withinSla=true count, withinSla=false count]
         */
        @Query("SELECT c.withinSla, COUNT(c) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.withinSla IS NOT NULL " +
                        "GROUP BY c.withinSla")
        List<Object[]> countBySlStatus();

        /**
         * Find claims in UNDER_REVIEW with no expected completion date set.
         * Indicates data integrity issue (claims submitted before SLA feature was
         * enabled).
         */
        @Query("SELECT c FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.status = 'UNDER_REVIEW' " +
                        "AND c.expectedCompletionDate IS NULL")
        List<Claim> findUnderReviewWithoutSla();

        /**
         * Calculate average SLA days configured across all claims.
         * Useful to track changes in system SLA setting over time.
         */
        @Query("SELECT AVG(c.slaDaysConfigured) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.slaDaysConfigured IS NOT NULL")
        Double getAverageSlaDaysConfigured();

        // ═══════════════════════════════════════════════════════════════════════════
        // PHASE 1: SERVICE TIMES LIMIT TRACKING QUERIES
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * DEPRECATED: serviceCategoryId field doesn't exist in Claim entity.
         * This query has been disabled. Use alternative approach with ClaimLines.
         * 
         * Refactor to count claims by service through ClaimLines
         * 
         * @deprecated serviceCategoryId removed from schema - use ClaimLine-based
         *             counting
         */
        @Deprecated
        default long countApprovedClaimsByMemberAndServiceInPeriod(
                        Long memberId,
                        Long serviceCategoryId,
                        LocalDate startDate,
                        LocalDate endDate) {
                // Temporarily returns 0 - service category tracking removed
                return 0L;
        }

        /**
         * Count submitted and approved claims by member and service code in a given
         * period.
         * Used for times-limit validation in benefit policies.
         */
        @Query("SELECT COUNT(DISTINCT c.id) FROM Claim c JOIN c.lines l " +
                        "WHERE c.member.id = :memberId " +
                        "AND l.serviceCode = :serviceCode " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.SUBMITTED, " +
                        "com.waad.tba.modules.claim.entity.ClaimStatus.UNDER_REVIEW, " +
                        "com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, " +
                        "com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED) " +
                        "AND c.active = true " +
                        "AND c.serviceDate BETWEEN :startDate AND :endDate")
        long countPendingAndApprovedClaimsByMemberAndServiceInPeriod(
                        @Param("memberId") Long memberId,
                        @Param("serviceCode") String serviceCode,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        // ═══════════════════════════════════════════════════════════════════════════════
        // VISIT-BASED QUERIES (Added 2026-01-14)
        // For Contract-First compliance
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Find claims by Visit ID.
         * Used to retrieve all claims associated with a specific visit.
         */
        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "LEFT JOIN FETCH m.benefitPolicy bp " +
                        " " +
                        "LEFT JOIN FETCH c.preAuthorization pa " +
                        "WHERE c.active = true " +
                        "AND c.visit.id = :visitId")
        List<Claim> findByVisitId(@Param("visitId") Long visitId);

        /**
         * Calculate total deductible applied for a member in a given year.
         * REPLACES: findByMemberId() + in-memory filter (N+1 performance fix)
         * 
         * @param memberId Member ID
         * @param year     Calendar year (e.g., 2026)
         * @param statuses Valid statuses to include
         * @return Sum of deductibleApplied for the year
         */
        @Query("SELECT COALESCE(SUM(c.deductibleApplied), 0) FROM Claim c " +
                        "WHERE c.member.id = :memberId " +
                        "AND YEAR(COALESCE(c.serviceDate, c.createdAt)) = :year " +
                        "AND c.status IN :statuses " +
                        "AND c.id <> :excludeClaimId")
        java.math.BigDecimal sumDeductibleForYear(
                        @Param("memberId") Long memberId,
                        @Param("year") int year,
                        @Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses,
                        @Param("excludeClaimId") Long excludeClaimId);

        /**
         * Calculate total patient out-of-pocket for a member in a given year.
         */
        @Query("SELECT COALESCE(SUM(c.patientCoPay + c.deductibleApplied), 0) FROM Claim c " +
                        "WHERE c.member.id = :memberId " +
                        "AND YEAR(COALESCE(c.serviceDate, c.createdAt)) = :year " +
                        "AND c.status IN :statuses " +
                        "AND c.id <> :excludeClaimId")
        java.math.BigDecimal sumPatientCopayForYear(
                        @Param("memberId") Long memberId,
                        @Param("year") int year,
                        @Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses,
                        @Param("excludeClaimId") Long excludeClaimId);

        @Query("SELECT COALESCE(SUM(c.approvedAmount), 0) FROM Claim c " +
                        "JOIN c.member m LEFT JOIN m.parent p " +
                        "WHERE (m.id = :principalId OR p.id = :principalId) " +
                        "AND YEAR(c.serviceDate) = :year " +
                        "AND c.status IN :statuses")
        java.math.BigDecimal sumApprovedAmountByFamilyAndYear(
                        @Param("principalId") Long principalId,
                        @Param("year") int year,
                        @Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses);

        /**
         * Calculate total approved amount for a member in a given year.
         * Used for per-member limit validation.
         */
        @Query("SELECT COALESCE(SUM(c.approvedAmount), 0) FROM Claim c " +
                        "WHERE c.member.id = :memberId " +
                        "AND YEAR(c.serviceDate) = :year " +
                        "AND c.status IN :statuses")
        java.math.BigDecimal sumApprovedAmountByMemberAndYear(
                        @Param("memberId") Long memberId,
                        @Param("year") int year,
                        @Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses);

        /**
         * Count service usage for a member in a given year.
         */
        @Query("SELECT COUNT(cl) FROM ClaimLine cl " +
                        "WHERE cl.claim.member.id = :memberId " +
                        "AND cl.serviceCode = :serviceCode " +
                        "AND YEAR(cl.claim.serviceDate) = :year " +
                        "AND cl.claim.status IN :statuses " +
                        "AND cl.rejected = false")
        long countServiceUsageByMemberAndYear(
                        @Param("memberId") Long memberId,
                        @Param("serviceCode") String serviceCode,
                        @Param("year") int year,
                        @Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses);

        /**
         * Find claim by claim number (unique identifier).
         */
        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "LEFT JOIN FETCH m.benefitPolicy bp " +
                        " " +
                        "LEFT JOIN FETCH c.preAuthorization pa " +
                        "WHERE c.active = true " +
                        "AND c.id = :claimNumber")
        java.util.Optional<Claim> findByClaimNumber(@Param("claimNumber") Long claimNumber);

        /**
         * Get financial summary statistics for reports.
         * Returns: [totalCount, totalRequested, totalApproved, totalRefused, totalPaid,
         * approvedCount, settledCount]
         *
         * NOTE: approvedAmount is summed for ALL non-DRAFT statuses PLUS DRAFT claims
         * that already
         * have an approvedAmount set (batch-entry backlog claims created directly as
         * processed).
         */
        @Query("SELECT COUNT(c), " +
                        "COALESCE(SUM(c.requestedAmount), 0), " +
                        "COALESCE(SUM(CASE WHEN c.status <> com.waad.tba.modules.claim.entity.ClaimStatus.DRAFT " +
                        "               OR (c.status = com.waad.tba.modules.claim.entity.ClaimStatus.DRAFT AND c.approvedAmount IS NOT NULL AND c.approvedAmount > 0) "
                        +
                        "               THEN c.approvedAmount ELSE 0 END), 0), " +
                        "COALESCE(SUM(CASE WHEN c.status <> com.waad.tba.modules.claim.entity.ClaimStatus.DRAFT " +
                        "               OR (c.status = com.waad.tba.modules.claim.entity.ClaimStatus.DRAFT AND c.approvedAmount IS NOT NULL AND c.approvedAmount > 0) "
                        +
                        "               THEN c.refusedAmount ELSE 0 END), 0), " +
                        "COALESCE(SUM(CASE WHEN c.netProviderAmount IS NOT NULL THEN c.netProviderAmount ELSE 0 END), 0), "
                        +
                        "COUNT(CASE WHEN c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED, com.waad.tba.modules.claim.entity.ClaimStatus.BATCHED) THEN 1 END), "
                        +
                        "COUNT(CASE WHEN c.status = com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED THEN 1 END) "
                        +
                        "FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND (:employerId IS NULL OR c.member.employer.id = :employerId) " +
                        "AND (:providerId IS NULL OR c.providerId = :providerId) " +
                        "AND (:status IS NULL OR c.status = :status) " +
                        "AND (CAST(:dateFrom AS date) IS NULL OR c.serviceDate >= :dateFrom) " +
                        "AND (CAST(:dateTo AS date) IS NULL OR c.serviceDate <= :dateTo)")
        List<Object[]> getFinancialSummary(
                        @Param("employerId") Long employerId,
                        @Param("providerId") Long providerId,
                        @Param("status") com.waad.tba.modules.claim.entity.ClaimStatus status,
                        @Param("dateFrom") LocalDate dateFrom,
                        @Param("dateTo") LocalDate dateTo);

        // ═══════════════════════════════════════════════════════════════════════════════
        // TICKET 1: ANNUAL LIMIT CONSUMPTION TRACKING (Phase Lite)
        // Sum APPROVED claims for member by benefit year
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Sum approved amounts for a specific member in a given year.
         * Uses only APPROVED and SETTLED claims for accurate consumption tracking.
         * 
         * @param memberId  The member ID
         * @param yearStart Start of the benefit year (e.g., 2026-01-01)
         * @param yearEnd   End of the benefit year (e.g., 2026-12-31)
         * @return Total approved amount for the member in that period (NULL-safe,
         *         defaults to 0)
         */
        @Query("SELECT COALESCE(SUM(c.approvedAmount), 0) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.member.id = :memberId " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED) "
                        +
                        "AND c.serviceDate >= :yearStart " +
                        "AND c.serviceDate <= :yearEnd")
        java.math.BigDecimal sumApprovedAmountsByMemberAndYear(
                        @Param("memberId") Long memberId,
                        @Param("yearStart") LocalDate yearStart,
                        @Param("yearEnd") LocalDate yearEnd);

        /**
         * Sum approved amounts for a specific member in a given category and year.
         * Used for category-level limit tracking.
         */
        @Query("SELECT COALESCE(SUM(l.approvedUnitPrice * l.approvedQuantity), 0) FROM Claim c " +
                        "JOIN c.lines l " +
                        "WHERE c.active = true " +
                        "AND c.member.id = :memberId " +
                        "AND l.appliedCategoryId = :categoryId " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, " +
                        "com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED, " +
                        "com.waad.tba.modules.claim.entity.ClaimStatus.BATCHED) " +
                        "AND c.serviceDate >= :yearStart " +
                        "AND c.serviceDate <= :yearEnd")
        java.math.BigDecimal sumApprovedAmountsByMemberAndCategoryAndYear(
                        @Param("memberId") Long memberId,
                        @Param("categoryId") Long categoryId,
                        @Param("yearStart") LocalDate yearStart,
                        @Param("yearEnd") LocalDate yearEnd);

        /**
         * Sum approved amounts for multiple categories (hierarchical tracking).
         */
        @Query("SELECT COALESCE(SUM(l.approvedUnitPrice * l.approvedQuantity), 0) FROM Claim c " +
                        "JOIN c.lines l " +
                        "WHERE c.active = true " +
                        "AND c.member.id = :memberId " +
                        "AND l.appliedCategoryId IN :categoryIds " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, " +
                        "com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED, " +
                        "com.waad.tba.modules.claim.entity.ClaimStatus.BATCHED) " +
                        "AND c.serviceDate >= :yearStart " +
                        "AND c.serviceDate <= :yearEnd " +
                        "AND (:excludeClaimId IS NULL OR c.id <> :excludeClaimId)")
        java.math.BigDecimal sumApprovedAmountsByMemberAndCategoriesAndYear(
                        @Param("memberId") Long memberId,
                        @Param("categoryIds") List<Long> categoryIds,
                        @Param("yearStart") LocalDate yearStart,
                        @Param("yearEnd") LocalDate yearEnd,
                        @Param("excludeClaimId") Long excludeClaimId);

        // ═══════════════════════════════════════════════════════════════════════════════
        // PHASE 6: Provider Portal Queries
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Count open claims filtered by provider ID.
         */
        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true AND c.providerId = :providerId AND (c.status = com.waad.tba.modules.claim.entity.ClaimStatus.DRAFT OR c.status = com.waad.tba.modules.claim.entity.ClaimStatus.SUBMITTED OR c.status = com.waad.tba.modules.claim.entity.ClaimStatus.UNDER_REVIEW OR c.status = com.waad.tba.modules.claim.entity.ClaimStatus.NEEDS_CORRECTION)")
        long countOpenClaimsByProvider(@Param("providerId") Long providerId);

        /**
         * Count approved claims filtered by provider ID.
         */
        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true AND c.providerId = :providerId AND c.status = com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED")
        long countApprovedClaimsByProvider(@Param("providerId") Long providerId);

        /**
         * Count outstanding claims for a provider (approved but not settled yet).
         * Includes APPROVED and BATCHED states.
         */
        @Query("SELECT COUNT(c) FROM Claim c WHERE c.active = true AND c.providerId = :providerId AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, com.waad.tba.modules.claim.entity.ClaimStatus.BATCHED)")
        long countOutstandingClaimsByProvider(@Param("providerId") Long providerId);

        /**
         * Sum approved amounts filtered by provider ID.
         */
        @Query("SELECT COALESCE(SUM(c.approvedAmount), 0) FROM Claim c WHERE c.active = true AND c.providerId = :providerId AND c.status = com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED")
        java.math.BigDecimal sumApprovedAmountsByProvider(@Param("providerId") Long providerId);

        /**
         * Sum settled amounts (net provider amount) filtered by provider ID.
         */
        @Query("SELECT COALESCE(SUM(COALESCE(c.netProviderAmount, c.approvedAmount)), 0) FROM Claim c WHERE c.active = true AND c.providerId = :providerId AND c.status = com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED")
        java.math.BigDecimal sumSettledAmountsByProvider(@Param("providerId") Long providerId);

        /**
         * Get recent claims by provider ID.
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT c.id as id, c.member.fullName as memberName, c.diagnosisDescription as diagnosisDescription, c.status as status, c.createdAt as createdAt FROM Claim c WHERE c.active = true AND c.providerId = :providerId ORDER BY c.createdAt DESC")
        List<RecentClaimProjection> getRecentClaimsByProvider(@Param("providerId") Long providerId, Pageable pageable);

        // ═══════════════════════════════════════════════════════════════════════════════
        // FINANCIAL SUMMARY QUERIES (SINGLE SOURCE OF TRUTH)
        // ═══════════════════════════════════════════════════════════════════════════════
        // ALL financial aggregations MUST use these queries.
        // Frontend is FORBIDDEN from calculating totals.
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Sum total requested amounts across all active claims.
         * 
         * @return SUM(requestedAmount) - COALESCE handles NULL safety
         */
        @Query("SELECT COALESCE(SUM(c.requestedAmount), 0) FROM Claim c WHERE c.active = true")
        java.math.BigDecimal sumTotalRequestedAmounts();

        /**
         * Sum total requested amounts filtered by employer.
         */
        @Query("SELECT COALESCE(SUM(c.requestedAmount), 0) FROM Claim c " +
                        "WHERE c.active = true AND c.member.employer.id = :employerOrgId")
        java.math.BigDecimal sumTotalRequestedAmountsByEmployer(@Param("employerOrgId") Long employerOrgId);

        /**
         * Sum total approved amounts filtered by employer.
         * Only includes APPROVED and SETTLED status claims.
         */
        @Query("SELECT COALESCE(SUM(c.approvedAmount), 0) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED)")
        java.math.BigDecimal sumApprovedAmountsForApprovedSettledByEmployer(@Param("employerOrgId") Long employerOrgId);

        /**
         * Sum total patient co-pay amounts.
         */
        @Query("SELECT COALESCE(SUM(c.patientCoPay), 0) FROM Claim c WHERE c.active = true")
        java.math.BigDecimal sumTotalPatientCoPay();

        /**
         * Sum total patient co-pay amounts filtered by employer.
         */
        @Query("SELECT COALESCE(SUM(c.patientCoPay), 0) FROM Claim c " +
                        "WHERE c.active = true AND c.member.employer.id = :employerOrgId")
        java.math.BigDecimal sumTotalPatientCoPayByEmployer(@Param("employerOrgId") Long employerOrgId);

        /**
         * Sum net provider amounts (amount owed to providers).
         * Falls back to approvedAmount if netProviderAmount is null.
         */
        @Query("SELECT COALESCE(SUM(COALESCE(c.netProviderAmount, c.approvedAmount)), 0) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED)")
        java.math.BigDecimal sumTotalNetProviderAmounts();

        /**
         * Sum net provider amounts filtered by employer.
         */
        @Query("SELECT COALESCE(SUM(COALESCE(c.netProviderAmount, c.approvedAmount)), 0) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED)")
        java.math.BigDecimal sumTotalNetProviderAmountsByEmployer(@Param("employerOrgId") Long employerOrgId);

        /**
         * Sum settled amounts (only SETTLED status).
         */
        @Query("SELECT COALESCE(SUM(COALESCE(c.netProviderAmount, c.approvedAmount)), 0) FROM Claim c " +
                        "WHERE c.active = true AND c.status = com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED")
        java.math.BigDecimal sumTotalSettledAmounts();

        /**
         * Sum settled amounts filtered by employer.
         */
        @Query("SELECT COALESCE(SUM(COALESCE(c.netProviderAmount, c.approvedAmount)), 0) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "AND c.status = com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED")
        java.math.BigDecimal sumTotalSettledAmountsByEmployer(@Param("employerOrgId") Long employerOrgId);

        /**
         * Sum difference amounts (requested - approved).
         */
        @Query("SELECT COALESCE(SUM(c.differenceAmount), 0) FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED)")
        java.math.BigDecimal sumTotalDifferenceAmounts();

        /**
         * Financial summary by provider - AUTHORITATIVE aggregation.
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT c.providerId as providerId, c.providerName as providerName, " +
                        "COUNT(c) as claimsCount, " +
                        "COALESCE(SUM(c.requestedAmount), 0) as requestedAmount, " +
                        "COALESCE(SUM(c.approvedAmount), 0) as approvedAmount, " +
                        "COALESCE(SUM(c.patientCoPay), 0) as patientCoPay, " +
                        "COALESCE(SUM(COALESCE(c.netProviderAmount, c.approvedAmount)), 0) as netProviderAmount " +
                        "FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED) "
                        +
                        "GROUP BY c.providerId, c.providerName " +
                        "ORDER BY COALESCE(SUM(c.approvedAmount), 0) DESC")
        List<FinancialSummaryByProviderProjection> getFinancialSummaryByProvider();

        /**
         * Financial summary by provider filtered by employer.
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT c.providerId as providerId, c.providerName as providerName, " +
                        "COUNT(c) as claimsCount, " +
                        "COALESCE(SUM(c.requestedAmount), 0) as requestedAmount, " +
                        "COALESCE(SUM(c.approvedAmount), 0) as approvedAmount, " +
                        "COALESCE(SUM(c.patientCoPay), 0) as patientCoPay, " +
                        "COALESCE(SUM(COALESCE(c.netProviderAmount, c.approvedAmount)), 0) as netProviderAmount " +
                        "FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "AND c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED) "
                        +
                        "GROUP BY c.providerId, c.providerName " +
                        "ORDER BY COALESCE(SUM(c.approvedAmount), 0) DESC")
        List<FinancialSummaryByProviderProjection> getFinancialSummaryByProviderAndEmployer(
                        @Param("employerOrgId") Long employerOrgId);

        /**
         * Financial summary by status - for pie charts and status breakdown.
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT c.status as status, COUNT(c) as count, " +
                        "COALESCE(SUM(c.requestedAmount), 0) as totalRequestedAmount, " +
                        "COALESCE(SUM(c.approvedAmount), 0) as totalApprovedAmount " +
                        "FROM Claim c " +
                        "WHERE c.active = true " +
                        "GROUP BY c.status")
        List<FinancialSummaryByStatusProjection> getFinancialSummaryByStatus();

        /**
         * Financial summary by status filtered by employer.
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT c.status as status, COUNT(c) as count, " +
                        "COALESCE(SUM(c.requestedAmount), 0) as totalRequestedAmount, " +
                        "COALESCE(SUM(c.approvedAmount), 0) as totalApprovedAmount " +
                        "FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.member.employer.id = :employerOrgId " +
                        "GROUP BY c.status")
        List<FinancialSummaryByStatusProjection> getFinancialSummaryByStatusAndEmployer(
                        @Param("employerOrgId") Long employerOrgId);

        // ═══════════════════════════════════════════════════════════════════════════════
        // MEDICAL REVIEW WORKBENCH - Inbox Items (2026-01-30)
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Financial summary by employer organization.
         * ✅ TYPE-SAFE: Uses interface projection instead of Object[]
         */
        @Query("SELECT c.member.employer.id as employerOrgId, c.member.employer.name as employerOrgName, " +
                        "COUNT(c) as claimsCount, " +
                        "COUNT(DISTINCT c.member.id) as membersCount, " +
                        "COALESCE(SUM(c.requestedAmount), 0) as requestedAmount, " +
                        "COALESCE(SUM(c.approvedAmount), 0) as approvedAmount " +
                        "FROM Claim c " +
                        "WHERE c.active = true " +
                        "AND c.member.employer IS NOT NULL " +
                        "GROUP BY c.member.employer.id, c.member.employer.name " +
                        "ORDER BY COALESCE(SUM(c.approvedAmount), 0) DESC")
        List<FinancialSummaryByEmployerProjection> getFinancialSummaryByEmployer();

        // ═══════════════════════════════════════════════════════════════════════════════
        // PROVIDER ACCOUNT SETTLEMENT - Batching Queries (Phase 2)
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Find claims by provider and status for settlement candidate evaluation.
         */
        @Query("SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member " +
                        "WHERE c.active = true " +
                        "AND c.providerId = :providerId " +
                        "AND c.status = :status " +
                        "ORDER BY c.createdAt ASC")
        List<Claim> findByProviderIdAndStatusForSettlement(
                        @Param("providerId") Long providerId,
                        @Param("status") com.waad.tba.modules.claim.entity.ClaimStatus status);

        // ═══════════════════════════════════════════════════════════════════════════════
        // MEDICAL REVIEWER ISOLATION - Filtered Queries (Phase: Reviewer Isolation
        // 2026-02-12)
        // ═══════════════════════════════════════════════════════════════════════════════

        /**
         * Search claims with pagination filtered by reviewer's assigned providers.
         * MEDICAL REVIEWER ISOLATION: Only shows claims from providers assigned to
         * reviewer.
         * 
         * @param keyword     Search keyword
         * @param providerIds List of provider IDs reviewer has access to
         * @param pageable    Pagination parameters
         * @return Page of claims from assigned providers only
         */
        @Query(value = "SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "LEFT JOIN FETCH m.benefitPolicy bp " +
                        " " +
                        "LEFT JOIN FETCH c.preAuthorization pa " +
                        "WHERE c.active = true " +
                        "AND c.providerId IN :providerIds " +
                        "AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(m.civilId) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))", countQuery = "SELECT COUNT(c) FROM Claim c LEFT JOIN c.member m "
                                        +
                                        "WHERE c.active = true AND c.providerId IN :providerIds " +
                                        "AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) "
                                        +
                                        "OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) "
                                        +
                                        "OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                                        "OR LOWER(m.civilId) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))")
        Page<Claim> searchPagedByReviewerProviders(
                        @Param("keyword") String keyword,
                        @Param("providerIds") List<Long> providerIds,
                        Pageable pageable);

        /**
         * Search claims with filters, restricted to reviewer's assigned providers.
         * MEDICAL REVIEWER ISOLATION: Combines existing filters with provider
         * isolation.
         * 
         * @param keyword     Search keyword
         * @param providerIds List of provider IDs reviewer has access to
         * @param employerId  Optional employer filter
         * @param status      Optional status filter
         * @param dateFrom    Optional service date from
         * @param dateTo      Optional service date to
         * @param pageable    Pagination parameters
         * @return Page of filtered claims from assigned providers only
         */
        @Query(value = "SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "LEFT JOIN FETCH m.benefitPolicy bp " +
                        "LEFT JOIN FETCH m.employer e " +
                        " " +
                        "LEFT JOIN FETCH c.preAuthorization pa " +
                        "WHERE c.active = true " +
                        "AND c.providerId IN :providerIds " +
                        "AND (:employerId IS NULL OR m.employer.id = :employerId) " +
                        "AND (:status IS NULL OR c.status = :status) " +
                        "AND (CAST(:dateFrom AS date) IS NULL OR c.serviceDate >= :dateFrom) " +
                        "AND (CAST(:dateTo AS date) IS NULL OR c.serviceDate <= :dateTo) " +
                        "AND (CAST(:createdAtFrom AS timestamp) IS NULL OR c.createdAt >= :createdAtFrom) " +
                        "AND (CAST(:createdAtTo AS timestamp) IS NULL OR c.createdAt < :createdAtTo) " +
                        "AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                        "OR LOWER(m.civilId) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))", countQuery = "SELECT COUNT(c) FROM Claim c LEFT JOIN c.member m "
                                        +
                                        "WHERE c.active = true AND c.providerId IN :providerIds " +
                                        "AND (:employerId IS NULL OR m.employer.id = :employerId) " +
                                        "AND (:status IS NULL OR c.status = :status) " +
                                        "AND (CAST(:dateFrom AS date) IS NULL OR c.serviceDate >= :dateFrom) " +
                                        "AND (CAST(:dateTo AS date) IS NULL OR c.serviceDate <= :dateTo) " +
                                        "AND (CAST(:createdAtFrom AS timestamp) IS NULL OR c.createdAt >= :createdAtFrom) "
                                        +
                                        "AND (CAST(:createdAtTo AS timestamp) IS NULL OR c.createdAt < :createdAtTo) " +
                                        "AND (LOWER(c.providerName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) "
                                        +
                                        "OR LOWER(c.diagnosisDescription) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) "
                                        +
                                        "OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
                                        "OR LOWER(m.civilId) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))")
        Page<Claim> searchPagedWithFiltersAndReviewerProviders(
                        @Param("keyword") String keyword,
                        @Param("providerIds") List<Long> providerIds,
                        @Param("employerId") Long employerId,
                        @Param("status") com.waad.tba.modules.claim.entity.ClaimStatus status,
                        @Param("dateFrom") java.time.LocalDate dateFrom,
                        @Param("dateTo") java.time.LocalDate dateTo,
                        @Param("createdAtFrom") java.time.LocalDateTime createdAtFrom,
                        @Param("createdAtTo") java.time.LocalDateTime createdAtTo,
                        Pageable pageable);

        /**
         * Find claims by status list, restricted to reviewer's assigned providers.
         * MEDICAL REVIEWER ISOLATION: Filter by status and reviewer's providers.
         * 
         * @param providerIds List of provider IDs reviewer has access to
         * @param statuses    List of claim statuses to filter
         * @param pageable    Pagination parameters
         * @return Page of claims matching status and assigned providers
         */
        @Query(value = "SELECT c FROM Claim c " +
                        "LEFT JOIN FETCH c.member m " +
                        "LEFT JOIN FETCH m.benefitPolicy bp " +
                        " " +
                        "LEFT JOIN FETCH c.preAuthorization pa " +
                        "WHERE c.active = true " +
                        "AND c.providerId IN :providerIds " +
                        "AND c.status IN :statuses", countQuery = "SELECT COUNT(c) FROM Claim c " +
                                        "WHERE c.active = true AND c.providerId IN :providerIds AND c.status IN :statuses")
        Page<Claim> findByStatusInAndReviewerProviders(
                        @Param("providerIds") List<Long> providerIds,
                        @Param("statuses") List<com.waad.tba.modules.claim.entity.ClaimStatus> statuses,
                        Pageable pageable);
}

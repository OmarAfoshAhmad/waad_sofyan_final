package com.waad.tba.modules.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.member.entity.Member;

import jakarta.persistence.LockModeType;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {

       /**
        * Find member by ID with a PESSIMISTIC_WRITE lock.
        * Used during claim approval to ensure atomic calculation of remaining limits.
        */
       @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT m FROM Member m WHERE m.id = :id")
       Optional<Member> findByIdWithLock(@Param("id") Long id);

       Optional<Member> findByCivilId(String civilId);

       Optional<Member> findByNationalNumber(String nationalNumber);

       Optional<Member> findByCardNumber(String cardNumber);

       /**
        * Find member by barcode (used for QR scanning and eligibility check).
        * Uses EntityGraph to eagerly fetch employer organization and benefit policy
        * to avoid LazyInitializationException and ensure eligibility data is complete.
        */
       @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "employer", "benefitPolicy" })
       Optional<Member> findByBarcode(String barcode);

       /**
        * Find member by card number with eager loading of employer and policy.
        * Used for eligibility checks when searching by card number instead of barcode.
        */
       @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "employer", "benefitPolicy" })
       @Query("SELECT m FROM Member m WHERE m.cardNumber = :cardNumber")
       Optional<Member> findByCardNumberWithDetails(@Param("cardNumber") String cardNumber);

       // Removed deprecated findByQrCodeValue to fix startup error (property
       // qrCodeValue doesn't exist)
       // ✅ FIX-M3: Removed deprecated findByEmployerId, countByEmployerId,
       // findByEmployerIdAndStatus
       // Use canonical methods: findByEmployerId, countByEmployerId instead

       // findByBenefitPolicyId is declared at the bottom of this interface

       List<Member> findByStatus(Member.MemberStatus status);

       boolean existsByCivilId(String civilId);

       boolean existsByCardNumber(String cardNumber);

       boolean existsByFullNameIgnoreCaseAndEmployerIdAndActiveTrue(String fullName, Long employerOrgId);

       boolean existsByFullNameIgnoreCaseAndParentIdAndRelationshipAndActiveTrue(
                     String fullName,
                     Long parentId,
                     Member.Relationship relationship);

       boolean existsByCivilIdAndIdNot(String civilId, Long id);

       boolean existsByCardNumberAndIdNot(String cardNumber, Long id);

       /**
        * 🔒 CRITICAL: Check if barcode exists (for collision prevention with
        * FamilyMember)
        * Used by BarcodeGeneratorService.generateUniqueBarcodeForFamilyMember()
        */
       boolean existsByBarcode(String barcode);

       // ✅ FIX-M3: Removed deprecated findByEmployerId(Pageable) - use
       // findByEmployerId instead

       // Duplicates removed (searchPagedByEmployerId, findByEmployerId) - see
       // Canoncial Model section below

       @Override
       @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "employer", "benefitPolicy" })
       @Query("SELECT m FROM Member m WHERE m.active = true")
       Page<Member> findAll(Pageable pageable);

       @Query(value = "SELECT m FROM Member m LEFT JOIN FETCH m.employer LEFT JOIN FETCH m.benefitPolicy WHERE " +
                     "m.active = true AND (" +
                     "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                     "LOWER(m.nationalNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                     "LOWER(m.barcode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                     "LOWER(m.cardNumber) LIKE LOWER(CONCAT('%', :search, '%')))", countQuery = "SELECT COUNT(m) FROM Member m WHERE m.active = true AND (LOWER(m.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(m.nationalNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(m.barcode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(m.cardNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
       Page<Member> searchPaged(@Param("search") String search, Pageable pageable);

       @Query("SELECT m FROM Member m LEFT JOIN FETCH m.employer LEFT JOIN FETCH m.benefitPolicy WHERE " +
                     "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                     "LOWER(m.civilId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                     "LOWER(m.cardNumber) LIKE LOWER(CONCAT('%', :query, '%'))")
       List<Member> search(@Param("query") String query);

       // REMOVED: findByInsuranceCompanyIdPaged and searchByInsuranceCompany
       // (Architecture Refactor 2025-12-27)
       // Insurance company filtering is not part of operational data access.
       // Use employer-based queries via employer instead.
       // See: COMPANY-EMPLOYER-REFACTOR-SUMMARY.md

       // ═══════════════════════════════════════════════════════════════════════════
       // ORGANIZATION-BASED QUERIES (Canonical Model)
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find all active member names by employer organization ID.
        * Used for efficient duplicate detection during bulk import.
        */
       @Query("SELECT LOWER(m.fullName) FROM Member m WHERE m.employer.id = :employerOrgId AND m.active = true")
       List<String> findActiveFullNamesByEmployerId(@Param("employerOrgId") Long employerOrgId);

       /**
        * Find all members by employer organization ID
        */
       List<Member> findByEmployerId(Long employerOrgId);

       /**
        * Find all active members by employer organization ID
        */
       List<Member> findByEmployerIdAndActiveTrue(Long employerOrgId);

       /**
        * Find all members by employer organization ID (paginated)
        */
       @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "employer", "benefitPolicy" })
       @Query("SELECT m FROM Member m WHERE m.employer.id = :employerOrgId AND m.active = true")
       Page<Member> findByEmployerId(@Param("employerOrgId") Long employerOrgId, Pageable pageable);

       /**
        * Count members by employer organization ID
        */
       long countByEmployerId(Long employerOrgId);

       /**
        * Count active members by employer organization ID
        * Used for dashboard statistics with employer filtering
        */
       long countByEmployerIdAndActiveTrue(Long employerOrgId);

       /**
        * Search members by employer organization ID (paginated)
        * FIXED: Added countQuery for proper pagination with FETCH JOIN
        */
       @Query(value = "SELECT m FROM Member m LEFT JOIN FETCH m.employer LEFT JOIN FETCH m.benefitPolicy WHERE m.employer.id = :employerOrgId AND m.active = true AND ("
                     +
                     "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                     "LOWER(m.nationalNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                     "LOWER(m.barcode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                     "LOWER(m.cardNumber) LIKE LOWER(CONCAT('%', :search, '%')))", countQuery = "SELECT COUNT(m) FROM Member m WHERE m.employer.id = :employerOrgId AND m.active = true AND (LOWER(m.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(m.nationalNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(m.barcode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(m.cardNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
       Page<Member> searchPagedByEmployerId(@Param("search") String search, @Param("employerOrgId") Long employerOrgId,
                     Pageable pageable);

       /**
        * Search members by employer organization ID (non-paginated)
        */
       @Query("SELECT m FROM Member m WHERE m.employer.id = :employerOrgId AND (" +
                     "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                     "LOWER(m.civilId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                     "LOWER(m.cardNumber) LIKE LOWER(CONCAT('%', :query, '%')))")
       List<Member> searchByEmployerId(@Param("query") String query, @Param("employerOrgId") Long employerOrgId);

       /**
        * Find members without a benefit policy for an employer
        */
       @Query("SELECT m FROM Member m WHERE m.employer.id = :employerOrgId AND m.benefitPolicy IS NULL")
       List<Member> findMembersWithoutBenefitPolicy(@Param("employerOrgId") Long employerOrgId);

       /**
        * Find members by benefit policy ID
        */
       List<Member> findByBenefitPolicyId(Long benefitPolicyId);

       /**
        * Count members by benefit policy ID
        */
       long countByBenefitPolicyId(Long benefitPolicyId);

       /**
        * Count active members by benefit policy ID (used by DeletionGuard)
        */
       long countByBenefitPolicyIdAndActiveTrue(Long benefitPolicyId);

       // ═══════════════════════════════════════════════════════════════════════════
       // PHASE 2: STATUS & CARD STATUS FILTERING
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find members by status and employer organization ID
        */
       List<Member> findByStatusAndEmployerId(Member.MemberStatus status, Long employerOrgId);

       /**
        * Find members by status and employer organization ID (paginated)
        */
       Page<Member> findByStatusAndEmployerId(Member.MemberStatus status, Long employerOrgId, Pageable pageable);

       /**
        * Find members by card status and employer organization ID
        */
       List<Member> findByCardStatusAndEmployerId(Member.CardStatus cardStatus, Long employerOrgId);

       /**
        * Find members by card status and employer organization ID (paginated)
        */
       Page<Member> findByCardStatusAndEmployerId(Member.CardStatus cardStatus, Long employerOrgId, Pageable pageable);

       /**
        * Find members by status, card status, and employer organization ID (paginated)
        */
       Page<Member> findByStatusAndCardStatusAndEmployerId(
                     Member.MemberStatus status,
                     Member.CardStatus cardStatus,
                     Long employerOrgId,
                     Pageable pageable);

       // ═══════════════════════════════════════════════════════════════════════════
       // ADVANCED SEARCH QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * 🔍 ELIGIBILITY SEARCH: Find members by full name containing search term -
        * CASE INSENSITIVE
        * Used by searchForEligibility() - Priority 3
        */
       @Query("SELECT m FROM Member m WHERE " +
                     "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
       List<Member> findByFullNameContainingIgnoreCase(@Param("name") String name);

       // ✅ FIX-M3: Removed deprecated findByNameContainingIgnoreCase and
       // findByNameContaining
       // Use findByFullNameContainingIgnoreCase for canonical clarity

       /**
        * Find members by name and employer organization ID - CASE INSENSITIVE
        */
       @Query("SELECT m FROM Member m WHERE m.employer.id = :employerOrgId AND " +
                     "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
       List<Member> findByNameContainingAndEmployerId(
                     @Param("name") String name,
                     @Param("employerOrgId") Long employerOrgId);

       /**
        * Alias for findByNameContainingAndEmployerId - explicit case-insensitive
        * naming
        */
       default List<Member> findByNameContainingIgnoreCaseAndEmployerId(String name, Long employerOrgId) {
              return findByNameContainingAndEmployerId(name, employerOrgId);
       }

       /**
        * Find member by civil ID and employer organization ID
        */
       Optional<Member> findByCivilIdAndEmployerId(String civilId, Long employerOrgId);

       /**
        * Find member by card number and employer organization ID
        */
       Optional<Member> findByCardNumberAndEmployerId(String cardNumber, Long employerOrgId);

       /**
        * Find members by phone containing search term
        */
       List<Member> findByPhoneContaining(String phone);

       /**
        * Find members by phone and employer organization ID
        */
       List<Member> findByPhoneContainingAndEmployerId(String phone, Long employerOrgId);

       // ═══════════════════════════════════════════════════════════════════════════════
       // DASHBOARD STATISTICS QUERIES (Phase A)
       // Aggregations using JPQL - No Lazy Loading, No Entities returned
       // ═══════════════════════════════════════════════════════════════════════════════

       /**
        * Count active members (status = 'ACTIVE')
        */
       @Query("SELECT COUNT(m) FROM Member m WHERE m.active = true AND m.status = 'ACTIVE'")
       long countActiveMembers();

       /**
        * Get monthly member growth trends
        * Returns: [year, month, count]
        */
       @Query("SELECT YEAR(m.joinDate) as year, MONTH(m.joinDate) as month, COUNT(m) as count " +
                     "FROM Member m WHERE m.active = true " +
                     "AND m.joinDate >= :startDate " +
                     "AND m.joinDate <= :endDate " +
                     "GROUP BY YEAR(m.joinDate), MONTH(m.joinDate) " +
                     "ORDER BY year, month")
       List<Object[]> getMonthlyGrowthTrends(@Param("startDate") java.time.LocalDate startDate,
                     @Param("endDate") java.time.LocalDate endDate);

       /**
        * Get recent members (for dashboard recent activities)
        * Returns: [id, fullName, createdAt]
        */
       @Query("SELECT m.id, " +
                     "m.fullName as name, " +
                     "m.createdAt " +
                     "FROM Member m " +
                     "WHERE m.active = true " +
                     "ORDER BY m.createdAt DESC")
       List<Object[]> getRecentMembers(Pageable pageable);

       /**
        * Count members created in date range (for growth calculation)
        */
       @Query("SELECT COUNT(m) FROM Member m " +
                     "WHERE m.active = true " +
                     "AND m.createdAt >= :startDate " +
                     "AND m.createdAt < :endDate")
       long countMembersInDateRange(@Param("startDate") java.time.LocalDateTime startDate,
                     @Param("endDate") java.time.LocalDateTime endDate);

       /**
        * Count members created in date range by employer organization (for growth
        * calculation)
        */
       @Query("SELECT COUNT(m) FROM Member m " +
                     "WHERE m.active = true " +
                     "AND m.employer.id = :employerOrgId " +
                     "AND m.createdAt >= :startDate " +
                     "AND m.createdAt < :endDate")
       long countMembersInDateRangeByEmployer(@Param("startDate") java.time.LocalDateTime startDate,
                     @Param("endDate") java.time.LocalDateTime endDate,
                     @Param("employerOrgId") Long employerOrgId);

       // ============================================================================
       // PHASE 2: FUZZY NAME SEARCH (Arabic Autocomplete)
       // ============================================================================

       /**
        * Search members by name using fuzzy matching with pg_trgm
        * Uses similarity scoring for ranked results
        * Minimum 3 characters required
        * Returns top 10 matches ordered by similarity DESC
        * 
        * @param searchTerm Search query (min 3 chars)
        * @return List of members ordered by similarity score (highest first)
        */
       @Query(value = "SELECT m.id, m.full_name, m.card_number, " +
                     "similarity(m.full_name, :searchTerm) as sim " +
                     "FROM members m " +
                     "WHERE similarity(m.full_name, :searchTerm) > 0.1 " +
                     "ORDER BY sim DESC, m.full_name ASC " +
                     "LIMIT 10", nativeQuery = true)
       List<Object[]> searchByNameFuzzy(@Param("searchTerm") String searchTerm);

       /**
        * Search members by name using ILIKE (fallback for simple queries)
        * Faster than similarity but less intelligent
        * Good for exact prefix matches
        * 
        * @param searchPattern Search pattern with wildcards (e.g., "%احمد%")
        * @return List of members ordered by full name
        */
       @Query("SELECT m FROM Member m " +
                     "WHERE LOWER(m.fullName) LIKE LOWER(:searchPattern) " +
                     "ORDER BY m.fullName ASC")
       List<Member> searchByNamePattern(@Param("searchPattern") String searchPattern);

       // ═══════════════════════════════════════════════════════════════════════════
       // UNIFIED MEMBER ARCHITECTURE - PRINCIPAL/DEPENDENT QUERIES
       // ═══════════════════════════════════════════════════════════════════════════

       /**
        * Find all PRINCIPAL members (parent_id = NULL).
        * These are heads of families who have barcodes.
        */
       @Query("SELECT m FROM Member m WHERE m.parent IS NULL")
       List<Member> findAllPrincipals();

       /**
        * Find all PRINCIPAL members (paginated).
        */
       @Query("SELECT m FROM Member m WHERE m.parent IS NULL")
       Page<Member> findAllPrincipals(Pageable pageable);

       /**
        * Find all DEPENDENT members (parent_id IS NOT NULL).
        * These are family members who use parent's barcode.
        */
       @Query("SELECT m FROM Member m WHERE m.parent IS NOT NULL")
       List<Member> findAllDependents();

       /**
        * Find all dependents for a specific principal member.
        * 
        * @param parentId Principal member ID
        * @return List of dependents
        */
       List<Member> findByParentId(Long parentId);

       /**
        * Delete all dependents of a principal member.
        */
       void deleteByParentId(Long parentId);

       /**
        * Find all active dependents for a specific principal member.
        * 
        * @param parentId Principal member ID
        * @return List of active dependents
        */
       List<Member> findByParentIdAndActiveTrue(Long parentId);

       /**
        * Count dependents for a principal member.
        *
        * @param parentId Principal member ID
        * @return Number of dependents
        */
       long countByParentId(Long parentId);

       /**
        * Count dependents of a specific relationship type for a principal member.
        * Used to determine the next ordinal suffix (W1, W2, S1, D1 …).
        *
        * @param parentId     Principal member ID
        * @param relationship Relationship type
        * @return Number of existing dependents with that relationship
        */
       long countByParentIdAndRelationship(Long parentId, Member.Relationship relationship);

       /**
        * Find principal member with all dependents (using fetch join).
        * Optimized for single query with all family members.
        * 
        * @param principalId Principal member ID
        * @return Principal member with dependents eagerly loaded
        */
       @Query("SELECT m FROM Member m LEFT JOIN FETCH m.dependents WHERE m.id = :principalId AND m.parent IS NULL")
       Optional<Member> findPrincipalWithDependents(@Param("principalId") Long principalId);

       /**
        * Find all dependents by relationship type.
        * 
        * @param relationship Relationship type (WIFE, SON, DAUGHTER, etc.)
        * @return List of dependents with this relationship
        */
       List<Member> findByRelationship(Member.Relationship relationship);

       /**
        * Find dependents by parent ID and relationship.
        * Example: Find all sons of a specific principal
        * 
        * @param parentId     Principal member ID
        * @param relationship Relationship type
        * @return List of matching dependents
        */
       List<Member> findByParentIdAndRelationship(Long parentId, Member.Relationship relationship);

       /**
        * Check if a member has any dependents.
        * 
        * @param principalId Principal member ID
        * @return true if has dependents, false otherwise
        */
       @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Member m WHERE m.parent.id = :principalId")
       boolean hasAnyDependents(@Param("principalId") Long principalId);

       /**
        * ✅ FIX-M1.1: Batch fetch dependents for multiple principals (N+1 PREVENTION)
        * Find all dependents by parent IDs in a single query.
        * Used to prevent N+1 queries when loading multiple principals with their
        * dependents.
        * 
        * @param parentIds List of principal member IDs
        * @return List of all dependents for the given principals
        */
       List<Member> findByParentIdIn(List<Long> parentIds);
}

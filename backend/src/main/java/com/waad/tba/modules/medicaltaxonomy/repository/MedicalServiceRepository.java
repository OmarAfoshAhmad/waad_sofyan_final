package com.waad.tba.modules.medicaltaxonomy.repository;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository for MedicalService entity (Reference Data).
 * 
 * Supports:
 * - CRUD operations
 * - Code-based lookups
 * - Category filtering
 * - Pre-authorization filtering
 * - Price range queries
 * - Active/inactive filtering
 */
@Repository
public interface MedicalServiceRepository extends JpaRepository<MedicalService, Long> {

  // ═══════════════════════════════════════════════════════════════════════════
  // BASIC QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find service by unique code
   */
  Optional<MedicalService> findByCode(String code);

  /**
   * Find service by exact name (for duplicate checking during import)
   * Uses findFirstBy to avoid IncorrectResultSizeDataAccessException when
   * duplicates exist
   */
  Optional<MedicalService> findFirstByName(String name);

  /**
   * Find service by exact Arabic name
   * Uses findFirstBy to avoid IncorrectResultSizeDataAccessException when
   * duplicates exist
   */
  Optional<MedicalService> findFirstByNameAr(String nameAr);

  /**
   * Find service by exact English name
   * Uses findFirstBy to avoid IncorrectResultSizeDataAccessException when
   * duplicates exist
   */
  Optional<MedicalService> findFirstByNameEn(String nameEn);

  /**
   * Check if code exists (for duplicate validation)
   */
  boolean existsByCode(String code);

  /**
   * Check if code exists (case-insensitive)
   */
  boolean existsByCodeIgnoreCase(String code);

  /**
   * Find all active services
   */
  List<MedicalService> findByActiveTrue();

  /**
   * Find all active services ordered by code (for dropdowns)
   */
  List<MedicalService> findByActiveTrueOrderByCode();

  /**
   * Find all active services - paginated
   */
  Page<MedicalService> findByActiveTrue(Pageable pageable);

  /**
   * Find all inactive services - paginated
   */
  Page<MedicalService> findByActiveFalse(Pageable pageable);

  // ═══════════════════════════════════════════════════════════════════════════
  // CATEGORY QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find all services in a category
   */
  List<MedicalService> findByCategoryId(Long categoryId);

  /**
   * Find all active services in a category
   */
  @Query("SELECT ms FROM MedicalService ms WHERE ms.categoryId = :categoryId AND ms.active = true")
  List<MedicalService> findActiveByCategoryId(@Param("categoryId") Long categoryId);

  /**
   * Find all active services in a category - paginated
   */
  @Query("SELECT ms FROM MedicalService ms WHERE ms.categoryId = :categoryId AND ms.active = true")
  Page<MedicalService> findActiveByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

  /**
   * Check if category has services (for delete validation)
   */
  boolean existsByCategoryId(Long categoryId);

  /**
   * Count services in a category
   */
  long countByCategoryId(Long categoryId);

  /**
   * Count active services in a category
   */
  @Query("SELECT COUNT(ms) FROM MedicalService ms WHERE ms.categoryId = :categoryId AND ms.active = true")
  long countActiveByCategoryId(@Param("categoryId") Long categoryId);

  // ═══════════════════════════════════════════════════════════════════════════
  // PRE-AUTHORIZATION QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find all services requiring pre-authorization
   */
  @Query("SELECT ms FROM MedicalService ms WHERE ms.requiresPA = true AND ms.active = true")
  List<MedicalService> findServicesRequiringPA();

  /**
   * Find all services requiring pre-authorization - paginated
   */
  @Query("SELECT ms FROM MedicalService ms WHERE ms.requiresPA = true AND ms.active = true")
  Page<MedicalService> findServicesRequiringPA(Pageable pageable);

  /**
   * Find services in category requiring PA
   */
  @Query("""
          SELECT ms FROM MedicalService ms
          WHERE ms.categoryId = :categoryId
            AND ms.requiresPA = true
            AND ms.active = true
      """)
  List<MedicalService> findServicesRequiringPAByCategory(@Param("categoryId") Long categoryId);

  // ═══════════════════════════════════════════════════════════════════════════
  // SEARCH QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Search by name - case insensitive
   */
  @Query("""
          SELECT ms FROM MedicalService ms
          WHERE ms.active = true
            AND (LOWER(ms.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                 OR LOWER(ms.nameAr) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                 OR LOWER(ms.nameEn) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                 OR LOWER(ms.code) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
      """)
  List<MedicalService> searchByName(@Param("searchTerm") String searchTerm);

  /**
   * Search by name with pagination
   */
  @Query("""
          SELECT ms FROM MedicalService ms
          WHERE ms.active = true
            AND (LOWER(ms.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                 OR LOWER(ms.nameAr) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                 OR LOWER(ms.nameEn) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                 OR LOWER(ms.code) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
      """)
  Page<MedicalService> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);

  /**
   * Advanced search with multiple filters
   */
  @Query("""
          SELECT ms FROM MedicalService ms
          WHERE (:searchTerm IS NULL
              OR LOWER(ms.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
              OR LOWER(ms.nameAr) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
              OR LOWER(ms.nameEn) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
              OR LOWER(ms.code) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            AND (:categoryId IS NULL OR ms.categoryId = :categoryId)
            AND (:requiresPA IS NULL OR ms.requiresPA = :requiresPA)
            AND (:minPrice IS NULL OR ms.basePrice >= :minPrice)
            AND (:maxPrice IS NULL OR ms.basePrice <= :maxPrice)
            AND ms.active = true
      """)
  Page<MedicalService> advancedSearch(
      @Param("searchTerm") String searchTerm,
      @Param("categoryId") Long categoryId,
      @Param("requiresPA") Boolean requiresPA,
      @Param("minPrice") BigDecimal minPrice,
      @Param("maxPrice") BigDecimal maxPrice,
      Pageable pageable);

  // ═══════════════════════════════════════════════════════════════════════════
  // PRICE QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find services within price range
   */
  @Query("""
          SELECT ms FROM MedicalService ms
          WHERE ms.basePrice BETWEEN :minPrice AND :maxPrice
            AND ms.active = true
      """)
  List<MedicalService> findByPriceRange(
      @Param("minPrice") BigDecimal minPrice,
      @Param("maxPrice") BigDecimal maxPrice);

  /**
   * Find services by minimum price
   */
  @Query("SELECT ms FROM MedicalService ms WHERE ms.basePrice >= :minPrice AND ms.active = true")
  List<MedicalService> findByBasePriceGreaterThanEqual(@Param("minPrice") BigDecimal minPrice);

  /**
   * Find services by maximum price
   */
  @Query("SELECT ms FROM MedicalService ms WHERE ms.basePrice <= :maxPrice AND ms.active = true")
  List<MedicalService> findByBasePriceLessThanEqual(@Param("maxPrice") BigDecimal maxPrice);

  // ═══════════════════════════════════════════════════════════════════════════
  // VALIDATION QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find by ID and ensure it's active
   */
  @Query("SELECT ms FROM MedicalService ms WHERE ms.id = :id AND ms.active = true")
  Optional<MedicalService> findActiveById(@Param("id") Long id);

  /**
   * Find by code and ensure it's active
   */
  @Query("SELECT ms FROM MedicalService ms WHERE ms.code = :code AND ms.active = true")
  Optional<MedicalService> findActiveByCode(@Param("code") String code);

  /**
   * Find multiple services by their codes
   * Useful for bulk operations
   */
  @Query("SELECT ms FROM MedicalService ms WHERE ms.code IN :codes")
  List<MedicalService> findByCodes(@Param("codes") List<String> codes);

  // ═══════════════════════════════════════════════════════════════════════════
  // BULK OPERATIONS
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Soft delete all medical services (set active = false)
   * 
   * @return number of updated records
   */
  @Modifying
  @Query("UPDATE MedicalService ms SET ms.active = false WHERE ms.active = true")
  int softDeleteAll();

  /**
   * Activate all medical services (set active = true)
   * 
   * @return number of updated records
   */
  @Modifying
  @Query("UPDATE MedicalService ms SET ms.active = true WHERE ms.active = false")
  int activateAll();

  /**
   * Find all services (including inactive) - for admin operations
   */
  @Query("SELECT ms FROM MedicalService ms")
  Page<MedicalService> findAllIncludingInactive(Pageable pageable);

  /**
   * Count active services
   */
  long countByActiveTrue();

  /**
   * Count inactive services
   */
  long countByActiveFalse();

  // ═══════════════════════════════════════════════════════════════════════════
  // LOOKUP QUERIES (For MedicalServiceSelector)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Unified lookup query for medical service selection
   * 
   * Returns services with full category context for display.
   * Searches across: code, nameAr, nameEn, categoryNameAr, categoryNameEn
   * 
   * SQL:
   * SELECT ms.*, mc.name_ar as category_name_ar, mc.name_en as category_name_en
   * FROM medical_services ms
   * LEFT JOIN medical_categories mc ON ms.category_id = mc.id
   * WHERE ms.active = true
   * AND (:query IS NULL OR LOWER(ms.code) LIKE LOWER('%q%')
   * OR LOWER(ms.name_ar) LIKE LOWER('%q%')
   * OR LOWER(ms.name_en) LIKE LOWER('%q%')
   * OR LOWER(mc.name_ar) LIKE LOWER('%q%')
   * OR LOWER(mc.name_en) LIKE LOWER('%q%'))
   * AND (:categoryId IS NULL OR ms.category_id = :categoryId)
   * ORDER BY mc.name, ms.name
   */
  @Query(value = """
      SELECT
          ms.id as id,
          ms.code as code,
          ms.name as name,
          ms.name_ar as nameAr,
          ms.name_en as nameEn,
          COALESCE(ms.category_id, msc_primary.category_id) as categoryId,
          mc.name as categoryName,
          mc.name_ar as categoryNameAr,
          mc.name_en as categoryNameEn
      FROM medical_services ms
          LEFT JOIN LATERAL (
              SELECT msc.category_id
              FROM medical_service_categories msc
              WHERE msc.service_id = ms.id
                  AND msc.active = true
              ORDER BY msc.is_primary DESC, msc.id ASC
              LIMIT 1
          ) msc_primary ON true
          LEFT JOIN medical_categories mc ON mc.id = COALESCE(ms.category_id, msc_primary.category_id)
      WHERE ms.active = true
        AND (:query IS NULL OR :query = ''
             OR LOWER(ms.code) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(ms.name) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(ms.name_ar) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(ms.name_en) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(mc.name) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(mc.name_ar) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(mc.name_en) LIKE LOWER(CONCAT('%', :query, '%')))
        AND (:categoryId IS NULL OR COALESCE(ms.category_id, msc_primary.category_id) = :categoryId
             OR EXISTS (SELECT 1 FROM medical_categories sc WHERE sc.id = COALESCE(ms.category_id, msc_primary.category_id) AND sc.parent_id = :categoryId))
      ORDER BY COALESCE(mc.name, 'zzz'), ms.name
      """, nativeQuery = true)
  List<MedicalServiceLookupProjection> lookupServices(
      @Param("query") String query,
      @Param("categoryId") Long categoryId);

  /**
   * Projection interface for lookup results
   */
  interface MedicalServiceLookupProjection {
    Long getId();

    String getCode();

    String getName();

    String getNameAr();

    String getNameEn();

    Long getCategoryId();

    String getCategoryName();

    String getCategoryNameAr();

    String getCategoryNameEn();
  }

  // ═══════════════════════════════ CATALOG HIERARCHY ═══════════════════════

  /** Find active non-deleted services belonging to a specific specialty */
  @Query("""
      SELECT ms FROM MedicalService ms
      WHERE ms.specialtyId = :specialtyId
        AND ms.deleted      = false
        AND ms.active       = true
      ORDER BY ms.name
      """)
  List<MedicalService> findActiveBySpecialtyId(@Param("specialtyId") Long specialtyId);
}

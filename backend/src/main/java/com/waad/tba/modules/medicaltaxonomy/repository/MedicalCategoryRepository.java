package com.waad.tba.modules.medicaltaxonomy.repository;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MedicalCategory entity (Reference Data).
 * 
 * Supports:
 * - CRUD operations
 * - Hierarchical queries (parent-child relationships)
 * - Code-based lookups
 * - Active/inactive filtering
 */
@Repository
public interface MedicalCategoryRepository extends JpaRepository<MedicalCategory, Long> {

    // ═══════════════════════════════════════════════════════════════════════════
    // BASIC QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find category by unique code
     */
    Optional<MedicalCategory> findByCode(String code);

    /**
     * Find category by exact name (for import mapping)
     */
    Optional<MedicalCategory> findFirstByName(String name);

    /**
     * Check if code exists (for duplicate validation)
     */
    boolean existsByCode(String code);

    /**
     * Check if code exists (case-insensitive)
     */
    boolean existsByCodeIgnoreCase(String code);

    /**
     * Find all active categories
     */
    List<MedicalCategory> findByActiveTrue();

    /**
     * Find all active categories - paginated
     */
    Page<MedicalCategory> findByActiveTrue(Pageable pageable);

    // ═══════════════════════════════════════════════════════════════════════════
    // HIERARCHY QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find all root categories (parentId is null)
     */
    List<MedicalCategory> findByParentIdIsNull();

    /**
     * Find all root categories (active only)
     */
    @Query("SELECT mc FROM MedicalCategory mc WHERE mc.parentId IS NULL AND mc.active = true")
    List<MedicalCategory> findRootCategories();

    /**
     * Find all direct children of a category
     */
    List<MedicalCategory> findByParentId(Long parentId);

    /**
     * Find all direct children of a category - paginated
     */
    Page<MedicalCategory> findByParentId(Long parentId, Pageable pageable);

    /**
     * Find all direct children of a category (active only)
     */
    @Query("SELECT mc FROM MedicalCategory mc WHERE mc.parentId = :parentId AND mc.active = true")
    List<MedicalCategory> findActiveChildrenByParentId(@Param("parentId") Long parentId);

    /**
     * Find categories belonging to a specific root (Phase 10 Many-to-Many)
     */
    @Query("SELECT mc FROM MedicalCategory mc JOIN mc.roots r WHERE r.id = :rootId AND mc.active = true")
    List<MedicalCategory> findByRootId(@Param("rootId") Long rootId);

    /**
     * Check if category has children (for delete validation)
     */
    boolean existsByParentId(Long parentId);

    /**
     * Count children of a category
     */
    long countByParentId(Long parentId);

    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Search by name - case insensitive
     */
    @Query("""
        SELECT mc FROM MedicalCategory mc
        WHERE LOWER(mc.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
          AND mc.active = true
    """)
    List<MedicalCategory> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Search by name with pagination
     */
    @Query("""
        SELECT mc FROM MedicalCategory mc
        WHERE LOWER(mc.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
          AND mc.active = true
    """)
    Page<MedicalCategory> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find by ID and ensure it's active
     */
    @Query("SELECT mc FROM MedicalCategory mc WHERE mc.id = :id AND mc.active = true")
    Optional<MedicalCategory> findActiveById(@Param("id") Long id);

    /**
     * Find by code and ensure it's active
     */
    @Query("SELECT mc FROM MedicalCategory mc WHERE mc.code = :code AND mc.active = true")
    Optional<MedicalCategory> findActiveByCode(@Param("code") String code);
}

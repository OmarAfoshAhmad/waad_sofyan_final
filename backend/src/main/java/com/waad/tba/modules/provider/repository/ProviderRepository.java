package com.waad.tba.modules.provider.repository;

import com.waad.tba.modules.provider.entity.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

       /**
        * Search active providers with pagination
        * Searches in name
        */
       @Query("SELECT p FROM Provider p " +
                     "WHERE p.active = true " +
                     "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                     "OR LOWER(p.licenseNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                     "OR LOWER(p.city) LIKE LOWER(CONCAT('%', :keyword, '%')))")
       Page<Provider> searchPaged(@Param("keyword") String keyword, Pageable pageable);

       /**
        * Search ALL providers (active and inactive) with pagination
        * Searches in name
        */
       @Query("SELECT p FROM Provider p " +
                     "WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                     "OR LOWER(p.licenseNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                     "OR LOWER(p.city) LIKE LOWER(CONCAT('%', :keyword, '%')))")
       Page<Provider> searchPagedAll(@Param("keyword") String keyword, Pageable pageable);

       @Query("SELECT p FROM Provider p WHERE p.active = true")
       List<Provider> findAllActive();

       /**
        * Find all active providers with pagination
        * PHASE 3 REVIEW: Added for selector endpoint pagination
        */
       @Query("SELECT p FROM Provider p WHERE p.active = true")
       Page<Provider> findAllActivePaged(Pageable pageable);

       @Query("SELECT COUNT(p) FROM Provider p WHERE p.active = true")
       long countActive();

       /**
        * Search providers by name or license number
        */
       @Query("SELECT p FROM Provider p " +
                     "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
                     "OR LOWER(p.licenseNumber) LIKE LOWER(CONCAT('%', :query, '%'))")
       List<Provider> search(@Param("query") String query);

       java.util.Optional<Provider> findByName(String name);

       boolean existsByName(String name);

       boolean existsByLicenseNumber(String licenseNumber);

       java.util.Optional<Provider> findByLicenseNumber(String licenseNumber);

       // ═══════════════════════════════════════════════════════════════════════════════
       // DASHBOARD STATISTICS QUERIES (Phase A)
       // Aggregations using JPQL - No Lazy Loading, No Entities returned
       // ═══════════════════════════════════════════════════════════════════════════════

       /**
        * Count active providers
        */
       @Query("SELECT COUNT(p) FROM Provider p WHERE p.active = true")
       long countActiveProviders();

       /**
        * Fetch multiple providers by IDs
        */
       List<Provider> findByIdIn(@Param("ids") java.util.Collection<Long> ids);

       /**
        * Find all active providers allowed for a specific employer
        * 1. Providers with allowAllEmployers = true
        * 2. Providers explicitly linked via provider_allowed_employers table
        */
       @Query("SELECT DISTINCT p FROM Provider p " +
              "LEFT JOIN p.allowedEmployers pae " +
              "WHERE p.active = true " +
              "AND (p.allowAllEmployers = true OR (pae.employer.id = :employerId AND pae.active = true))")
       List<Provider> findByAllowedEmployer(@Param("employerId") Long employerId);
}

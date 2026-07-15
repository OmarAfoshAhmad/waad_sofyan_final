package com.waad.tba.modules.employer.repository;

import com.waad.tba.modules.employer.entity.Employer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Employer entities.
 * Note: Future architecture plans to consolidate into a unified
 * OrganizationRepository.
 */
public interface EmployerRepository extends JpaRepository<Employer, Long> {

    List<Employer> findByActiveTrue();
    Page<Employer> findByActiveTrue(Pageable pageable);
    Page<Employer> findByActiveFalse(Pageable pageable);

    @Query("SELECT e FROM Employer e WHERE (:active IS NULL OR e.active = :active) " +
           "AND (:q = '' OR LOWER(e.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(e.code) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(COALESCE(e.email, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Employer> searchPage(@Param("active") Boolean active, @Param("q") String q, Pageable pageable);

    long countByActiveTrue();

    Optional<Employer> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Find employer by name (case-insensitive exact match)
     */
    Optional<Employer> findByNameIgnoreCase(String name);

    /**
     * Find the default employer
     */
    Optional<Employer> findByIsDefaultTrue();
}

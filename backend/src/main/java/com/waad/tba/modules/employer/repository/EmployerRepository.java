package com.waad.tba.modules.employer.repository;

import com.waad.tba.modules.employer.entity.Employer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Employer entities.
 * Note: Future architecture plans to consolidate into a unified
 * OrganizationRepository.
 */
public interface EmployerRepository extends JpaRepository<Employer, Long> {

    List<Employer> findByActiveTrue();

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

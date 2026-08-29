package com.waad.tba.modules.employer.repository;

import com.waad.tba.modules.employer.entity.Employer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import jakarta.persistence.LockModeType;
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

    /**
     * @param unscoped true only for a caller whose scope is GLOBAL. Every other
     *                 caller passes false and the employers they may reach, so
     *                 the narrowing happens in the query -- filtering the page
     *                 afterwards would leave the total count describing
     *                 somebody else's book
     */
    @Query("SELECT e FROM Employer e WHERE (:active IS NULL OR e.active = :active) " +
           "AND (:unscoped = true OR e.id IN :employerIds) " +
           "AND (:q = '' OR LOWER(e.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(e.code) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(COALESCE(e.email, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Employer> searchPage(@Param("active") Boolean active, @Param("q") String q,
            @Param("unscoped") boolean unscoped,
            @Param("employerIds") java.util.Collection<Long> employerIds,
            Pageable pageable);

    long countByActiveTrue();

    Optional<Employer> findByCode(String code);

    /**
     * Locked for the duration of the transaction that archives or restores
     * this employer.
     *
     * archive() is read-check-write across two tables: it counts assignments,
     * then flips active. Without a lock here, a member assignment being
     * written concurrently under MemberEmployerResolver.assignEmployer can
     * race it -- the count is taken before the new row lands, archive proceeds
     * believing nobody belongs to the employer, and the row that would have
     * blocked it commits a moment later under an employer that is now
     * archived. The lock serialises the two: whichever transaction reaches
     * this employer first, the second sees its committed result.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employer e WHERE e.id = :id")
    Optional<Employer> findByIdForLifecycleTransition(@Param("id") Long id);

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

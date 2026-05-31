package com.waad.tba.modules.visit.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.visit.entity.Visit;

@Repository
public interface VisitRepository extends JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {
    
    List<Visit> findByMemberId(Long memberId);
    
    // Data-level filtering method for explicit employer filtering
    @Query("SELECT v FROM Visit v WHERE v.member.employer.id = :employerId")
    List<Visit> findByMemberEmployerId(@Param("employerId") Long employerId);
    
    // PHASE 5.B: Paginated employer filtering with FETCH JOIN for member
    @Query(value = "SELECT v FROM Visit v " +
           "LEFT JOIN FETCH v.member m " +
           "WHERE v.member.employer.id = :employerId",
           countQuery = "SELECT COUNT(v) FROM Visit v WHERE v.member.employer.id = :employerId")
    Page<Visit> findByMemberEmployerId(@Param("employerId") Long employerId, Pageable pageable);
    
    // PHASE 5.B: Search with employer filtering - FETCH JOIN for member
    @Query(value = "SELECT v FROM Visit v " +
           "LEFT JOIN FETCH v.member m " +
           "WHERE v.member.employer.id = :employerId AND (" +
           "LOWER(v.doctorName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.specialty) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.diagnosis) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :q, '%')))",
           countQuery = "SELECT COUNT(v) FROM Visit v LEFT JOIN v.member m " +
           "WHERE v.member.employer.id = :employerId AND (" +
           "LOWER(v.doctorName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.specialty) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.diagnosis) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Visit> searchPagedByEmployerId(@Param("q") String q, @Param("employerId") Long employerId, Pageable pageable);
    
    // Count by employer
    @Query("SELECT COUNT(v) FROM Visit v WHERE v.member.employer.id = :employerId")
    long countByMemberEmployerId(@Param("employerId") Long employerId);
    
    @Query("SELECT v FROM Visit v LEFT JOIN v.member m WHERE " +
           "LOWER(v.doctorName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(v.specialty) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(v.diagnosis) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Visit> search(@Param("query") String query);

    // PHASE 5.B: Search paginated with FETCH JOIN for member
    @Query(value = "SELECT v FROM Visit v " +
           "LEFT JOIN FETCH v.member m " +
           "WHERE LOWER(v.doctorName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.specialty) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.diagnosis) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :q, '%'))",
           countQuery = "SELECT COUNT(v) FROM Visit v LEFT JOIN v.member m " +
           "WHERE LOWER(v.doctorName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.specialty) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.diagnosis) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Visit> searchPaged(@Param("q") String q, Pageable pageable);

    // PROVIDER filtering - visits by provider ID (providerId is Long field, not relation)
    List<Visit> findByProviderId(Long providerId);

    @Query(value = "SELECT v FROM Visit v LEFT JOIN FETCH v.member m WHERE v.providerId = :providerId",
           countQuery = "SELECT COUNT(v) FROM Visit v WHERE v.providerId = :providerId")
    Page<Visit> findByProviderId(@Param("providerId") Long providerId, Pageable pageable);

    // ═══════════════════════════════════════════════════════════════════════════
    // PROVIDER DATA ISOLATION (2026-01-16): Search with provider filtering
    // ════════════════════════════════════════════════════════════════════════════
    @Query(value = "SELECT v FROM Visit v " +
           "LEFT JOIN FETCH v.member m " +
           "WHERE v.providerId = :providerId AND (" +
           "LOWER(v.doctorName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.specialty) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.diagnosis) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :q, '%')))",
           countQuery = "SELECT COUNT(v) FROM Visit v LEFT JOIN v.member m " +
           "WHERE v.providerId = :providerId AND (" +
           "LOWER(v.doctorName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.specialty) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(v.diagnosis) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Visit> searchPagedByProviderId(@Param("q") String q, @Param("providerId") Long providerId, Pageable pageable);

    /**
     * Count visits by provider ID (for Provider Portal dashboard)
     */
    @Query("SELECT COUNT(v) FROM Visit v WHERE v.providerId = :providerId")
    long countByProviderId(@Param("providerId") Long providerId);

    // ==================== NEW FLOW (2026-01-13) ====================
    
    /**
     * Find visits by member with active=true
     */
    @Query("SELECT v FROM Visit v WHERE v.member.id = :memberId AND v.active = true ORDER BY v.visitDate DESC")
    List<Visit> findByMemberIdAndActiveTrue(@Param("memberId") Long memberId);
    
    /**
     * Find visits by provider with active=true (for Provider Documents Center)
     */
    @Query("SELECT v FROM Visit v LEFT JOIN FETCH v.member WHERE v.providerId = :providerId AND v.active = true ORDER BY v.visitDate DESC")
    List<Visit> findByProviderIdAndActiveTrue(@Param("providerId") Long providerId);

    @Query("SELECT DISTINCT v.member.id FROM Visit v WHERE v.member.id IN :memberIds")
    List<Long> findMemberIdsWithVisits(@Param("memberIds") java.util.Collection<Long> memberIds);
}


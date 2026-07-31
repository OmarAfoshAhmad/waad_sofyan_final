package com.waad.tba.modules.medicaldictionary.repository;

import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionarySynonym;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicalDictionarySynonymRepository extends JpaRepository<MedicalDictionarySynonym, Long> {

    boolean existsByNormalizedSynonym(String normalizedSynonym);

    long countByEntry_Id(Long entryId);

    Page<MedicalDictionarySynonym> findByEntry_Id(Long entryId, Pageable pageable);

    @Query(value = """
            SELECT s FROM MedicalDictionarySynonym s
            JOIN FETCH s.entry e
            LEFT JOIN FETCH e.medicalCategory c
            WHERE (:activeOnly = false OR s.active = true)
              AND (s.normalizedSynonym LIKE CONCAT('%', :q, '%')
                   OR e.normalizedCanonicalName LIKE CONCAT('%', :q, '%')
                   OR LOWER(COALESCE(c.code, '')) LIKE CONCAT('%', :q, '%'))
            """,
            countQuery = """
            SELECT COUNT(s) FROM MedicalDictionarySynonym s
            JOIN s.entry e
            LEFT JOIN e.medicalCategory c
            WHERE (:activeOnly = false OR s.active = true)
              AND (s.normalizedSynonym LIKE CONCAT('%', :q, '%')
                   OR e.normalizedCanonicalName LIKE CONCAT('%', :q, '%')
                   OR LOWER(COALESCE(c.code, '')) LIKE CONCAT('%', :q, '%'))
            """)
    Page<MedicalDictionarySynonym> searchSynonyms(@Param("q") String normalizedQuery,
                                                  @Param("activeOnly") boolean activeOnly,
                                                  Pageable pageable);

    @Query("""
            SELECT s FROM MedicalDictionarySynonym s
            JOIN FETCH s.entry e
            LEFT JOIN FETCH e.medicalCategory c
            WHERE s.active = true AND s.normalizedSynonym LIKE CONCAT('%', :q, '%')
            ORDER BY s.usageCount DESC, s.synonym ASC
            """)
    List<MedicalDictionarySynonym> searchActiveSynonyms(@Param("q") String normalizedQuery);
}

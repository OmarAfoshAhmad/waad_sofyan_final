package com.waad.tba.modules.medicaldictionary.repository;

import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionaryEntry;
import com.waad.tba.modules.medicaldictionary.enums.DictionaryEntryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MedicalDictionaryEntryRepository extends JpaRepository<MedicalDictionaryEntry, Long> {

    boolean existsByNormalizedCanonicalName(String normalizedCanonicalName);

    @EntityGraph(attributePaths = {"medicalCategory", "synonyms"})
    @Query("SELECT e FROM MedicalDictionaryEntry e LEFT JOIN FETCH e.synonyms LEFT JOIN FETCH e.medicalCategory WHERE e.id = :id")
    Optional<MedicalDictionaryEntry> findWithSynonymsById(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT e FROM MedicalDictionaryEntry e
            WHERE (:status IS NULL OR e.status = :status)
              AND (:q IS NULL OR e.normalizedCanonicalName LIKE CONCAT('%', :q, '%')
                   OR EXISTS (SELECT 1 FROM MedicalDictionarySynonym sx
                              WHERE sx.entry = e AND sx.active = true AND sx.normalizedSynonym LIKE CONCAT('%', :q, '%')))
            """)
    Page<MedicalDictionaryEntry> search(@Param("q") String normalizedQuery,
                                        @Param("status") DictionaryEntryStatus status,
                                        Pageable pageable);
}



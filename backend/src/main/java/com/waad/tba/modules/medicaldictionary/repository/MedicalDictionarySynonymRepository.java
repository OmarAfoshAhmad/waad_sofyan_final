package com.waad.tba.modules.medicaldictionary.repository;

import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionarySynonym;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicalDictionarySynonymRepository extends JpaRepository<MedicalDictionarySynonym, Long> {

    boolean existsByNormalizedSynonym(String normalizedSynonym);

    @Query("""
            SELECT s FROM MedicalDictionarySynonym s
            JOIN FETCH s.entry e
            JOIN FETCH e.medicalCategory c
            WHERE s.active = true AND s.normalizedSynonym LIKE CONCAT('%', :q, '%')
            ORDER BY s.usageCount DESC, s.synonym ASC
            """)
    List<MedicalDictionarySynonym> searchActiveSynonyms(@Param("q") String normalizedQuery);
}

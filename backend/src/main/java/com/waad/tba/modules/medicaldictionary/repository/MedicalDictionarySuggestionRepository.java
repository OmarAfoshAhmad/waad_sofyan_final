package com.waad.tba.modules.medicaldictionary.repository;

import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionarySuggestion;
import com.waad.tba.modules.medicaldictionary.enums.DictionarySuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalDictionarySuggestionRepository extends JpaRepository<MedicalDictionarySuggestion, Long> {

    Page<MedicalDictionarySuggestion> findByStatus(DictionarySuggestionStatus status, Pageable pageable);

    long countByStatus(DictionarySuggestionStatus status);
}

package com.waad.tba.modules.semantic.repository;

import com.waad.tba.modules.semantic.entity.MedicalSynonym;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalSynonymRepository extends JpaRepository<MedicalSynonym, Long> {
    List<MedicalSynonym> findByIsActiveTrue();
}

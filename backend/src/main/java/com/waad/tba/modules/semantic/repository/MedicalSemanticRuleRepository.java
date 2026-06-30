package com.waad.tba.modules.semantic.repository;

import com.waad.tba.modules.semantic.entity.MedicalSemanticRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalSemanticRuleRepository extends JpaRepository<MedicalSemanticRule, Long> {
    List<MedicalSemanticRule> findByIsActiveTrueOrderByPriorityDesc();
}

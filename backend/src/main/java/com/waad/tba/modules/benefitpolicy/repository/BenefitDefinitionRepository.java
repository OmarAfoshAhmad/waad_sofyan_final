package com.waad.tba.modules.benefitpolicy.repository;

import com.waad.tba.modules.benefitpolicy.entity.BenefitDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BenefitDefinitionRepository extends JpaRepository<BenefitDefinition, Long> {
    Optional<BenefitDefinition> findByCode(String code);
}

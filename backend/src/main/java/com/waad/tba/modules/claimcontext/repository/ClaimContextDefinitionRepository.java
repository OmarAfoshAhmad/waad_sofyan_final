package com.waad.tba.modules.claimcontext.repository;

import com.waad.tba.modules.claimcontext.entity.ClaimContextDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClaimContextDefinitionRepository extends JpaRepository<ClaimContextDefinition, String> {
    List<ClaimContextDefinition> findByActiveTrueOrderByDisplayOrderAscCodeAsc();
    Optional<ClaimContextDefinition> findByCodeAndActiveTrue(String code);
}

package com.waad.tba.modules.claimcontext.repository;

import com.waad.tba.modules.claimcontext.entity.ClaimContextDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClaimContextDefinitionRepository extends JpaRepository<ClaimContextDefinition, String> {
    List<ClaimContextDefinition> findByActiveTrueOrderByDisplayOrderAscCodeAsc();
}

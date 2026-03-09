package com.waad.tba.modules.medicaltaxonomy.repository;

import com.waad.tba.modules.medicaltaxonomy.entity.ServiceAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ServiceAlias} — searchable display aliases for medical
 * services.
 */
@Repository
public interface ServiceAliasRepository extends JpaRepository<ServiceAlias, Long> {

    /**
     * Find all aliases for a given medical service ID.
     */
    List<ServiceAlias> findByMedicalServiceId(Long medicalServiceId);

    /**
     * Find a single alias by its exact text and locale (case-sensitive).
     * Used by auto-matching to resolve alias → medical service.
     */
    Optional<ServiceAlias> findByAliasTextIgnoreCaseAndLocale(String aliasText, String locale);

    /**
     * Find any alias matching the given text (all locales).
     * Used for broader alias-based auto-matching.
     */
    Optional<ServiceAlias> findFirstByAliasTextIgnoreCase(String aliasText);

    /**
     * Check if an alias already exists for a given medical service and text.
     * Used to avoid duplicate alias entries when confirming provider service
     * mappings.
     */
    boolean existsByMedicalServiceIdAndAliasTextIgnoreCase(Long medicalServiceId, String aliasText);
}

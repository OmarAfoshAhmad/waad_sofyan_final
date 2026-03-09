package com.waad.tba.modules.medical.repository;

import com.waad.tba.modules.medical.entity.ProviderRawService;
import com.waad.tba.modules.medical.enums.MappingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link ProviderRawService}.
 */
@Repository
public interface ProviderRawServiceRepository extends JpaRepository<ProviderRawService, Long> {

    /**
     * List raw services for a provider filtered by mapping status.
     * Used by the mapping center UI to display the work queue (e.g.
     * status=PENDING).
     */
    List<ProviderRawService> findByProviderIdAndStatus(Long providerId, MappingStatus status);

    /**
     * List all raw services for a provider (any status).
     */
    List<ProviderRawService> findByProviderId(Long providerId);

    /**
     * Find existing raw service row for same provider/name (case-insensitive)
     * to support idempotent Excel ingestion.
     */
    java.util.Optional<ProviderRawService> findFirstByProviderIdAndRawNameIgnoreCase(Long providerId, String rawName);

    /** Count raw services by status (for stats). */
    long countByStatus(MappingStatus status);

    /** Count distinct provider IDs that have at least one raw service. */
    @Query("SELECT COUNT(DISTINCT r.providerId) FROM ProviderRawService r")
    long countDistinctProviders();
}

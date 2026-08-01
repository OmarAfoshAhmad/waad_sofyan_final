package com.waad.tba.modules.provider.repository;

import com.waad.tba.modules.provider.entity.ProviderAdminDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Provider Administrative Documents
 */
@Repository
public interface ProviderAdminDocumentRepository extends JpaRepository<ProviderAdminDocument, Long> {

    /**
     * Find all documents for a provider
     */
    List<ProviderAdminDocument> findByProviderId(Long providerId);

    /**
     * Batch document counts for a page of providers — ONE query for the whole
     * page instead of one count-per-row.
     */
    @Query("SELECT d.providerId, COUNT(d) FROM ProviderAdminDocument d WHERE d.providerId IN :providerIds GROUP BY d.providerId")
    List<Object[]> countByProviderIdIn(@Param("providerIds") List<Long> providerIds);
    
    /**
     * Find documents by provider and type
     */
    List<ProviderAdminDocument> findByProviderIdAndType(Long providerId, String type);
    
    /**
     * Check if document exists for provider
     */
    boolean existsByProviderIdAndId(Long providerId, Long id);
}

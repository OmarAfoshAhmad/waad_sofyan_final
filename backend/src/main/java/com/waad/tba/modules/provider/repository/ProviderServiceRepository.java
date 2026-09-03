package com.waad.tba.modules.provider.repository;

import com.waad.tba.modules.provider.entity.ProviderService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ProviderService Repository
 * 
 * Manages many-to-many relationship between Provider and MedicalService.
 */
@Repository
public interface ProviderServiceRepository extends JpaRepository<ProviderService, Long> {

    // ═══════════════════════════════════════════════════════════════════════════
    // FIND BY PROVIDER
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find all active service assignments for a provider
     */
    @Query("SELECT ps FROM ProviderService ps WHERE ps.providerId = :providerId AND ps.active = true")
    List<ProviderService> findActiveByProviderId(@Param("providerId") Long providerId);

    /**
     * Find all service assignments for a provider (including inactive)
     */
    @Query("SELECT ps FROM ProviderService ps WHERE ps.providerId = :providerId")
    List<ProviderService> findAllByProviderId(@Param("providerId") Long providerId);

    /**
     * Count active services for a provider
     */
    @Query("SELECT COUNT(ps) FROM ProviderService ps WHERE ps.providerId = :providerId AND ps.active = true")
    long countActiveByProviderId(@Param("providerId") Long providerId);

    // ═══════════════════════════════════════════════════════════════════════════
    // FIND BY SERVICE CODE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find all active providers offering a specific service
     */
    @Query("SELECT ps FROM ProviderService ps WHERE ps.serviceCode = :serviceCode AND ps.active = true")
    List<ProviderService> findActiveByServiceCode(@Param("serviceCode") String serviceCode);

    /**
     * Count providers offering a specific service
     */
    @Query("SELECT COUNT(ps) FROM ProviderService ps WHERE ps.serviceCode = :serviceCode AND ps.active = true")
    long countProvidersByServiceCode(@Param("serviceCode") String serviceCode);

    // ═══════════════════════════════════════════════════════════════════════════
    // EXISTENCE CHECKS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if provider offers a specific service (active assignment)
     */
    @Query("SELECT CASE WHEN COUNT(ps) > 0 THEN true ELSE false END " +
           "FROM ProviderService ps " +
           "WHERE ps.providerId = :providerId AND ps.serviceCode = :serviceCode AND ps.active = true")
    boolean existsByProviderIdAndServiceCode(@Param("providerId") Long providerId, 
                                             @Param("serviceCode") String serviceCode);

    /**
     * Check if provider has any active services
     */
    @Query("SELECT CASE WHEN COUNT(ps) > 0 THEN true ELSE false END " +
           "FROM ProviderService ps " +
           "WHERE ps.providerId = :providerId AND ps.active = true")
    boolean hasActiveServices(@Param("providerId") Long providerId);

    // ═══════════════════════════════════════════════════════════════════════════
    // FIND SPECIFIC ASSIGNMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find active assignment for provider and service
     */
    @Query("SELECT ps FROM ProviderService ps " +
           "WHERE ps.providerId = :providerId AND ps.serviceCode = :serviceCode AND ps.active = true")
    Optional<ProviderService> findActiveByProviderIdAndServiceCode(@Param("providerId") Long providerId,
                                                                    @Param("serviceCode") String serviceCode);

    /**
     * Find assignment for provider and service (including inactive)
     */
    @Query("SELECT ps FROM ProviderService ps " +
           "WHERE ps.providerId = :providerId AND ps.serviceCode = :serviceCode")
    Optional<ProviderService> findByProviderIdAndServiceCode(@Param("providerId") Long providerId,
                                                              @Param("serviceCode") String serviceCode);

    /**
     * Every existing assignment (active or not) across a set of providers and
     * service codes, in one query -- the read side of bulk provisioning
     * across many providers must not become N (or N×M) round trips.
     */
    @Query("SELECT ps FROM ProviderService ps " +
           "WHERE ps.providerId IN :providerIds AND ps.serviceCode IN :serviceCodes")
    List<ProviderService> findAllByProviderIdInAndServiceCodeIn(
            @Param("providerIds") List<Long> providerIds,
            @Param("serviceCodes") List<String> serviceCodes);

    // ═══════════════════════════════════════════════════════════════════════════
    // BULK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get distinct service codes offered by a provider
     */
    @Query("SELECT DISTINCT ps.serviceCode FROM ProviderService ps " +
           "WHERE ps.providerId = :providerId AND ps.active = true " +
           "ORDER BY ps.serviceCode")
    List<String> findServiceCodesByProviderId(@Param("providerId") Long providerId);

    /**
     * Get distinct provider IDs offering a specific service
     */
    @Query("SELECT DISTINCT ps.providerId FROM ProviderService ps " +
           "WHERE ps.serviceCode = :serviceCode AND ps.active = true")
    List<Long> findProviderIdsByServiceCode(@Param("serviceCode") String serviceCode);

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Count total active service assignments
     */
    @Query("SELECT COUNT(ps) FROM ProviderService ps WHERE ps.active = true")
    long countAllActive();

    /**
     * Find providers offering multiple services
     * 
     * Returns providers with service count >= minServices
     */
    @Query("SELECT ps.providerId, COUNT(ps) as serviceCount " +
           "FROM ProviderService ps " +
           "WHERE ps.active = true " +
           "GROUP BY ps.providerId " +
           "HAVING COUNT(ps) >= :minServices")
    List<Object[]> findProvidersWithMinimumServices(@Param("minServices") int minServices);
}

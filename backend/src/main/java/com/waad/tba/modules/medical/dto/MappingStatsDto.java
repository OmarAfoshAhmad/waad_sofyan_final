package com.waad.tba.modules.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics DTO for GET /api/v1/medical-services-mapping/stats
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingStatsDto {

    /** Total provider raw services across all providers */
    private long total;

    /** Raw services with status = PENDING */
    private long pending;

    /** Raw services with status = MANUAL_CONFIRMED or AUTO_MATCHED */
    private long mapped;

    /** Raw services with status = REJECTED */
    private long rejected;

    /** Number of distinct providers that have at least one raw service */
    private long providersWithRawServices;

    /** Number of unified medical services in the catalog */
    private long medicalServicesTotal;
}

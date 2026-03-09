package com.waad.tba.modules.medical.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for POST /api/v1/medical-services-mapping/link-and-map
 *
 * Links raw service IDs to an existing MedicalService.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkAndMapRequest {

    /** Existing medical_services.id to link raw services to */
    @NotNull(message = "معرّف الخدمة الطبية مطلوب")
    @Positive
    private Long medicalServiceId;

    /** Raw service IDs (provider_raw_services.id) to map */
    @NotEmpty(message = "يجب تحديد خدمة واحدة على الأقل")
    private List<Long> rawServiceIds;
}

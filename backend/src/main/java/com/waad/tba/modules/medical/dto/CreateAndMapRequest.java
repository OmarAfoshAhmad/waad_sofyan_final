package com.waad.tba.modules.medical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for POST /api/v1/medical-services-mapping/create-and-map
 *
 * Creates a new MedicalService (status=ACTIVE) and maps the listed raw service
 * IDs to it via provider_service_mappings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAndMapRequest {

    /** Unified catalog service name (Arabic) */
    @NotBlank(message = "اسم الخدمة مطلوب")
    @Size(max = 200)
    private String name;

    /**
     * Unique service code — e.g. "SRV-LAB-001".
     * Must be unique across medical_services.
     */
    @NotBlank(message = "كود الخدمة مطلوب")
    @Size(max = 50)
    private String code;

    /** Category this service belongs to (mandatory for ACTIVE services) */
    @NotNull(message = "التصنيف مطلوب")
    @Positive
    private Long categoryId;

    /** Raw service IDs (provider_raw_services.id) to map to the new service */
    @NotEmpty(message = "يجب تحديد خدمة واحدة على الأقل")
    private List<Long> rawServiceIds;
}

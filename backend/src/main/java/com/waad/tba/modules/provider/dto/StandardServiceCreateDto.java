package com.waad.tba.modules.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Creates a new standard (invoice-priced, MANUAL_AMOUNT) professional
 * service -- e.g. a new medication-invoice or optics-fitting catalog entry,
 * the kind V215 seeded four of by migration. pricingMode is not a field
 * here: this endpoint exists specifically to create MANUAL_AMOUNT services,
 * never CONTRACT_PRICE ones, so there is nothing to choose.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StandardServiceCreateDto {

    @NotBlank(message = "رمز الخدمة إلزامي")
    private String code;

    @NotBlank(message = "الاسم بالعربية إلزامي")
    private String nameAr;

    private String nameEn;

    @NotNull(message = "التصنيف الطبي إلزامي")
    private Long categoryId;

    /** Facility types this service is auto-suggested/applied for by default. */
    @Builder.Default
    private List<com.waad.tba.modules.provider.entity.Provider.ProviderType> defaultProviderTypes = List.of();
}

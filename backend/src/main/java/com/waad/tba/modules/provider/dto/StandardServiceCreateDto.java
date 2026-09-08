package com.waad.tba.modules.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.math.BigDecimal;

import com.waad.tba.modules.medicaltaxonomy.enums.PricingMode;

/**
 * Creates a shared professional standard service. These services are
 * invoice-priced MANUAL_AMOUNT catalog entries. Claim-entry ad-hoc services are
 * stored on the active provider contract instead, so they can keep an open
 * per-claim unit price without becoming a global fixed-price catalog item.
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

    @NotBlank(message = "سياق الاستخدام إلزامي")
    private String defaultClaimContextCode;

    @Builder.Default
    private PricingMode pricingMode = PricingMode.MANUAL_AMOUNT;

    private BigDecimal basePrice;

    /** Facility types this service is auto-suggested/applied for by default. */
    @Builder.Default
    private List<com.waad.tba.modules.provider.entity.Provider.ProviderType> defaultProviderTypes = List.of();
}

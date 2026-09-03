package com.waad.tba.modules.claim.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.claim.api.response.ClaimResponse;
import com.waad.tba.modules.claim.api.request.CreateClaimRequest;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;

/**
 * Verifies companyDiscountAmount survives the DTO -> API response conversion.
 * Before this fix, ClaimApiMapper.toResponse() dropped the field entirely --
 * ClaimResponse had no place to put it, so the settlement screen had no
 * choice but to invent its own (wrong) 10% company-share figure client-side.
 */
class ClaimApiMapperTest {

    private final ClaimApiMapper mapper = new ClaimApiMapper();

    @Test
    void companyDiscountAmountFlowsThroughToTheApiResponse() {
        ClaimViewDto dto = ClaimViewDto.builder()
                .id(1L)
                .requestedAmount(new BigDecimal("1000.00"))
                .patientCoPay(new BigDecimal("100.00"))
                .refusedAmount(new BigDecimal("200.00"))
                .companyDiscountAmount(new BigDecimal("63.00"))
                .approvedAmount(new BigDecimal("637.00"))
                .netProviderAmount(new BigDecimal("637.00"))
                .build();

        ClaimResponse response = mapper.toResponse(dto);

        assertThat(response.getCompanyDiscountAmount()).isEqualByComparingTo(new BigDecimal("63.00"));
        assertThat(response.getNetProviderAmount()).isEqualByComparingTo(new BigDecimal("637.00"));
    }

    @Test
    void nullCompanyDiscountAmountIsPassedThroughAsNullNotZero() {
        // Legacy claims saved before companyDiscountAmount existed should not be
        // silently coerced to zero by the API layer -- the caller must be able
        // to tell "no discount" apart from "not captured for this old claim".
        ClaimViewDto dto = ClaimViewDto.builder()
                .id(2L)
                .requestedAmount(new BigDecimal("500.00"))
                .build();

        ClaimResponse response = mapper.toResponse(dto);

        assertThat(response.getCompanyDiscountAmount()).isNull();
    }

    @Test
    void explicitManualRefusalIsTheOnlyRefusalAcceptedAsCommandInput() {
        CreateClaimRequest request = CreateClaimRequest.builder()
                .lines(java.util.List.of(CreateClaimRequest.ClaimLineRequest.builder()
                        .quantity(1)
                        .manualRefusedAmount(new BigDecimal("125.00"))
                        .refusedAmount(new BigDecimal("4250.00"))
                        .build()))
                .build();

        ClaimCreateDto mapped = mapper.toCreateDto(request);

        assertThat(mapped.getLines().get(0).getManualRefusedAmount())
                .isEqualByComparingTo("125.00");
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyRefusedAmountRemainsACompatibleAliasForOlderClients() {
        CreateClaimRequest request = CreateClaimRequest.builder()
                .lines(java.util.List.of(CreateClaimRequest.ClaimLineRequest.builder()
                        .quantity(1)
                        .refusedAmount(new BigDecimal("50.00"))
                        .build()))
                .build();

        ClaimCreateDto mapped = mapper.toCreateDto(request);

        assertThat(mapped.getLines().get(0).getManualRefusedAmount())
                .isEqualByComparingTo("50.00");
    }
}

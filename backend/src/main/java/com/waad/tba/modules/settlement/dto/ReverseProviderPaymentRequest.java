package com.waad.tba.modules.settlement.dto;

import lombok.Data;

@Data
public class ReverseProviderPaymentRequest {
    private String reason;
    private Long expectedPaymentVersion;
    private Long expectedAccountVersion;
}

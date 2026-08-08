package com.waad.tba.modules.settlement.dto;

import lombok.Data;

/** Echoes back the versions the accountant last saw, so a stale screen cannot post silently. */
@Data
public class PostProviderPaymentRequest {
    private Long expectedPaymentVersion;
    private Long expectedAccountVersion;
}

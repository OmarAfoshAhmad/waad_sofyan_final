package com.waad.tba.modules.settlement.dto;

import lombok.Data;

@Data
public class AdjustProviderAccountRequest {
    private String reason;
    private Long expectedAccountVersion;
}

package com.waad.tba.modules.providercontract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractImportPreviewResultDto {
    private String sessionId;
    private int totalRows;
    private int validCount;
    private int invalidCount;
    private List<ContractImportRowDto> rows;
}

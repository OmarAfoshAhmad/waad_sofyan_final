package com.waad.tba.modules.benefitpolicy.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkCoverageCheckDto {
    private Long memberId;
    private Integer year;
    private Long excludeClaimId;
    private List<BulkCoverageLineDto> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkCoverageLineDto {
        private String id;
        private Long serviceId;
        private Long categoryId;
        private Long serviceCategoryId;
    }
}

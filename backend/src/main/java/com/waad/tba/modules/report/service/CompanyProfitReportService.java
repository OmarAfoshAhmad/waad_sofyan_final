package com.waad.tba.modules.report.service;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.report.dto.CompanyProfitReportRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyProfitReportService {

    private final ClaimRepository claimRepository;

    @Transactional(readOnly = true)
    public List<CompanyProfitReportRowDto> getCompanyProfitReport(Long employerId, Integer year, Integer month, Long providerId) {
        if (year == null) {
            throw new IllegalArgumentException("Year is required for this report");
        }

        List<ClaimStatus> approvedStatuses = Arrays.asList(
                ClaimStatus.APPROVED,
                ClaimStatus.BATCHED,
                ClaimStatus.SETTLED
        );

        List<CompanyProfitReportRowDto> results = claimRepository.getCompanyProfitReport(employerId, year, month, providerId, approvedStatuses);
        
        for (CompanyProfitReportRowDto row : results) {
            if (row.getCompanyDueValue() == null || row.getCompanyDueValue().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                java.math.BigDecimal total = row.getTotalClaimValue() != null ? row.getTotalClaimValue() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal discount = row.getDiscountPercent() != null ? row.getDiscountPercent() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal calculatedDue = total.multiply(discount).divide(new java.math.BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                row.setCompanyDueValue(calculatedDue);
            }
        }
        
        return results;
    }
}

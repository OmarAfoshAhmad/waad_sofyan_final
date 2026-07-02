package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.PriceVarianceStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Validates and calculates pricing variance for Pre-Authorization Line Items.
 * This is a core component of the Enterprise Pre-Auth Portal to prevent financial leakage.
 */
@Service
public class PreAuthPricingValidator {

    private static final BigDecimal HIGH_VARIANCE_THRESHOLD = new BigDecimal("10.00");
    private static final BigDecimal CRITICAL_VARIANCE_THRESHOLD = new BigDecimal("25.00");

    /**
     * Processes the line item, calculates requested amounts, variances, and assigns a status.
     * 
     * @param line The PreAuthorizationLine to process
     */
    public void processPricing(PreAuthorizationLine line) {

        // 1. Unlisted Procedure Logic
        if (line.getSourceType() == PreAuthorizationLine.SourceType.UNLISTED) {
            line.setContractPrice(null);
            line.setVarianceAmount(null);
            line.setVariancePercentage(null);
            line.setPriceVarianceStatus(PriceVarianceStatus.UNLISTED);
            line.setRequiresPriceReview(true);
            
            if (line.getManualPrice() != null) {
                line.setRequestedAmount(line.getManualPrice());
            } else {
                line.setPriceVarianceStatus(PriceVarianceStatus.MISSING_PRICE);
                line.setRequestedAmount(BigDecimal.ZERO);
            }
            return;
        }

        // 2. Missing Contract Price (Should not happen for CONTRACTED, but safety check)
        if (line.getContractPrice() == null) {
            line.setPriceVarianceStatus(PriceVarianceStatus.MISSING_PRICE);
            line.setRequestedAmount(BigDecimal.ZERO);
            return;
        }

        // 3. Manual Price Logic
        if (line.getManualPrice() == null) {
            // No manual price = EXACT match to contract
            line.setRequestedAmount(line.getContractPrice());
            line.setVarianceAmount(BigDecimal.ZERO);
            line.setVariancePercentage(BigDecimal.ZERO);
            line.setPriceVarianceStatus(PriceVarianceStatus.MATCH_CONTRACT);
            line.setRequiresPriceReview(false);
            return;
        }

        // 4. Manual Price Provided - Calculate Variance
        line.setRequestedAmount(line.getManualPrice());
        
        BigDecimal varianceAmount = line.getManualPrice().subtract(line.getContractPrice());
        line.setVarianceAmount(varianceAmount);

        int comparison = line.getManualPrice().compareTo(line.getContractPrice());

        if (comparison == 0) {
            line.setVariancePercentage(BigDecimal.ZERO);
            line.setPriceVarianceStatus(PriceVarianceStatus.MATCH_CONTRACT);
            line.setRequiresPriceReview(false);
        } else if (comparison < 0) {
            // Manual price is LOWER than contract (e.g. discount)
            BigDecimal variancePercentage = calculatePercentage(varianceAmount, line.getContractPrice());
            line.setVariancePercentage(variancePercentage);
            line.setPriceVarianceStatus(PriceVarianceStatus.BELOW_CONTRACT);
            line.setRequiresPriceReview(true); // Should review why they are giving a discount
        } else {
            // Manual price is HIGHER than contract
            BigDecimal variancePercentage = calculatePercentage(varianceAmount, line.getContractPrice());
            line.setVariancePercentage(variancePercentage);
            line.setRequiresPriceReview(true);

            if (variancePercentage.compareTo(CRITICAL_VARIANCE_THRESHOLD) >= 0) {
                line.setPriceVarianceStatus(PriceVarianceStatus.CRITICAL_VARIANCE);
            } else if (variancePercentage.compareTo(HIGH_VARIANCE_THRESHOLD) >= 0) {
                line.setPriceVarianceStatus(PriceVarianceStatus.HIGH_VARIANCE);
            } else {
                line.setPriceVarianceStatus(PriceVarianceStatus.ABOVE_CONTRACT);
            }
        }
    }

    private BigDecimal calculatePercentage(BigDecimal varianceAmount, BigDecimal contractPrice) {
        if (contractPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // (variance / contractPrice) * 100
        return varianceAmount.multiply(new BigDecimal("100"))
                .divide(contractPrice, 2, RoundingMode.HALF_UP);
    }
}

package com.waad.tba.modules.medicaltaxonomy.enums;

/**
 * How a claim line's unit price is determined for this service.
 *
 * CONTRACT_PRICE: resolved from the provider's contract pricing item,
 * effective on the claim's service date (the default, unchanged path).
 *
 * MANUAL_AMOUNT: the clerk enters the invoice amount directly on the claim
 * line (e.g. a pharmacy or optics invoice total) -- there is no fixed price
 * list for this service, so no ProviderContractPricingItem is looked up.
 */
public enum PricingMode {
    CONTRACT_PRICE,
    MANUAL_AMOUNT
}

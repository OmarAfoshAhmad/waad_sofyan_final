package com.waad.tba.modules.providercontract.event;

import java.util.Set;

/** Published synchronously inside the pricing write transaction. */
public record ProviderPricingItemsDeactivatedEvent(Set<Long> pricingItemIds) {
}

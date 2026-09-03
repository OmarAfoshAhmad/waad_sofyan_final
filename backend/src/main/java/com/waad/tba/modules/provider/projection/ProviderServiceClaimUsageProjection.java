package com.waad.tba.modules.provider.projection;

/** One (provider, standard service) pair that already has claim history. */
public interface ProviderServiceClaimUsageProjection {
    Long getProviderId();

    String getServiceCode();
}

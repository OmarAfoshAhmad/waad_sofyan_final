package com.waad.tba.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Feature Flags Configuration (Phase 10).
 * 
 * Controls which claim entry modes are active.
 * Toggle via application.yml or environment variables.
 */
@Data
@Component
@ConfigurationProperties(prefix = "waad.features")
public class FeatureFlagsConfig {
    
    /**
     * Master switch for the entire Provider Portal.
     * If false, providers cannot access direct claim entry endpoints.
     */
    private boolean providerPortalEnabled = false;
    
    /**
     * Allow direct claim creation from provider side (VISIT-based).
     */
    private boolean directClaimSubmissionEnabled = false;
    
    /**
     * Is the Batches mode active?
     */
    private boolean batchClaimsEnabled = true;
}

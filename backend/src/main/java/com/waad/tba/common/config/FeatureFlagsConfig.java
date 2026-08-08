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
     * Allow direct pre-authorization creation from provider side.
     */
    private boolean directPreauthSubmissionEnabled = false;
    
    /**
     * Is the Batches mode active?
     */
    private boolean batchClaimsEnabled = true;

    /**
     * Master switch for the new provider-payment write path (draft/post/reverse,
     * account-adjustment). Off by default: Phase 9 builds the UI and wires its
     * reads, but no write action may reach ProviderPaymentPostingService /
     * ProviderPaymentReversalService / ProviderAccountAdjustmentService until
     * Phase 11 explicitly turns this on. Reads (suggestion, reconciliation) are
     * never gated by this flag — only actions that move money or write the ledger.
     */
    private boolean providerPaymentPostingEnabled = false;
}

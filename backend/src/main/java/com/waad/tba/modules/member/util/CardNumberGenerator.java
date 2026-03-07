package com.waad.tba.modules.member.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @deprecated Use {@link com.waad.tba.modules.member.service.CardNumberGeneratorService} instead.
 * This class uses in-memory AtomicLong which loses state on restart and doesn't work
 * in clustered/multi-instance deployments. The service class uses PostgreSQL SEQUENCE
 * (member_card_number_seq) for proper distributed unique ID generation.
 * 
 * <p><strong>SECURITY WARNING:</strong> AtomicLong-based ID generation causes:
 * <ul>
 *   <li>Duplicate card numbers after server restart</li>
 *   <li>Race conditions in multi-instance deployments</li>
 *   <li>Non-deterministic sequence gaps</li>
 * </ul>
 * 
 * @see com.waad.tba.modules.member.service.CardNumberGeneratorService
 */
@Deprecated(since = "Phase 2 Remediation", forRemoval = true)
@SuppressWarnings("dep-ann")
public class CardNumberGenerator {
    
    /**
     * Thread-safe atomic counter.
     * Initialized to System.currentTimeMillis() % 1_000_000_000 to ensure
     * uniqueness across restarts while keeping numbers reasonable.
     */
    private static final AtomicLong sequence = new AtomicLong(
        System.currentTimeMillis() % 1_000_000_000L
    );
    
    /**
     * Generate a unique card number with format: WAAD|MEMBER|{SEQUENCE}
     * 
     * @return Card number (e.g., WAAD|MEMBER|735234859)
     */
    public static String generate() {
        long nextValue = sequence.incrementAndGet();
        // Format to 9 digits with zero-padding
        String sequenceStr = String.format("%09d", nextValue);
        return "WAAD|MEMBER|" + sequenceStr;
    }
    
    /**
     * Get current sequence value (for testing/debugging only).
     * 
     * @return Current sequence value
     */
    public static long getCurrentSequence() {
        return sequence.get();
    }
}

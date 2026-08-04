package com.waad.tba.modules.maintenancehub.service;

import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueRegistration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Central entry point every maintenance-center detector registers findings through.
 *
 * The point of this class — and the reason it exists at all — is deduplication by
 * {@code fingerprint}: detecting "the same" problem twice must not create two rows.
 * A repeat detection of an OPEN/IN_PROGRESS issue just bumps its occurrence counter;
 * a repeat detection of a RESOLVED/IGNORED issue flips it back to REOPENED, because a
 * problem that was marked fixed and came back is itself worth surfacing. See
 * {@link IssueRegistryWriter} for the actual read-modify-write logic.
 *
 * Best-effort by design, mirroring SystemErrorLogService: a detector calling this must
 * never have its own work broken by a ledger failure, so every exception is caught and
 * logged here, never propagated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssueRegistry {

    /**
     * The pessimistic lock in {@link IssueRegistryWriter} only guards a fingerprint
     * that already has a row — it cannot prevent two brand-new detections of the same
     * fingerprint from racing on INSERT. When that happens the unique constraint lets
     * exactly one through and aborts the rest; retrying (in a fresh transaction — the
     * failed one is unusable after a Postgres constraint violation) lets the loser see
     * the winner's now-committed row and correctly take the update-existing path
     * instead of losing the detection entirely.
     */
    private static final int MAX_ATTEMPTS = 3;

    private final IssueRegistryWriter writer;

    /**
     * Registers a detection. Never throws.
     *
     * @return the affected issue's id, or {@code null} if registration failed.
     */
    public Long register(IssueRegistration registration) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return writer.register(registration);
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("[MAINT-HUB] Fingerprint conflict persisted after {} attempts (fingerprint={})",
                            MAX_ATTEMPTS, registration == null ? null : registration.fingerprint());
                    return null;
                }
                // Expected under concurrent first-detections of the same fingerprint;
                // not logged at warn level since a retry resolves it in the common case.
            } catch (Exception e) {
                log.warn("[MAINT-HUB] Failed to register issue (type={}, fingerprint={}): {}",
                        registration == null ? null : registration.issueType(),
                        registration == null ? null : registration.fingerprint(),
                        e.getMessage());
                return null;
            }
        }
        return null;
    }
}

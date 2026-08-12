package com.waad.tba.modules.eligibility.service;

import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Facade for persisting the eligibility audit trail. Deliberately NOT
 * @Transactional itself -- it delegates the actual write to
 * EligibilityAuditWriter, a separate bean whose own REQUIRES_NEW transaction
 * genuinely isolates the write from the caller's transaction (both
 * family-eligibility entry points call checkEligibility from inside
 * @Transactional(readOnly = true), which would otherwise make every audit
 * INSERT fail against Postgres and drag the whole eligibility check down
 * with it -- see EligibilityAuditWriter's Javadoc for why REQUIRES_NEW plus
 * a distinct bean, and not a method-local annotation, is required here).
 *
 * Splitting the try/catch into this outer, non-transactional bean is equally
 * deliberate: catching a write failure from INSIDE the same
 * @Transactional(REQUIRES_NEW) method that produced it doesn't work --
 * Hibernate/Spring mark that transaction rollback-only at the point of
 * failure regardless of whether application code catches the exception, so
 * swallowing it there and returning normally still throws
 * UnexpectedRollbackException from the transaction interceptor. Catching one
 * level up, after the writer's (now-doomed, already-rolled-back) transaction
 * has fully unwound, is the only way to turn a write failure into a plain
 * boolean instead of an exception that reaches the eligibility check itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EligibilityAuditRecorder {

    private final EligibilityAuditWriter writer;

    /**
     * Persists one audit record for a completed eligibility decision.
     * Never throws -- a failure here must never affect the eligibility
     * decision it is describing. Returns whether the record was actually
     * persisted, so the caller can mark the decision's auditRecorded flag
     * accordingly instead of silently claiming the audit trail is complete
     * when it isn't.
     */
    public boolean record(EligibilityContext context, EligibilityResult result) {
        try {
            writer.write(context, result);
            log.debug("[Eligibility] Audit log saved - RequestID: {}", context.getRequestId());
            return true;
        } catch (Exception e) {
            // Alertable marker: never let an eligibility decision look untraceable
            // without this being loud in the logs. Don't fail the eligibility
            // check itself -- the caller decides how to reflect this in the result.
            log.error("[Eligibility][AUDIT_LOG_FAILURE] Failed to persist eligibility audit record - "
                            + "requestId={}, memberId={}: {}",
                    context.getRequestId(), context.getMemberId(), e.getMessage(), e);
            return false;
        }
    }
}

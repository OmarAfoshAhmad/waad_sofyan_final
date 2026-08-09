package com.waad.tba.modules.settlement.event;

import com.waad.tba.modules.claim.service.ClaimReversalOrchestrator;
import com.waad.tba.modules.claim.service.ClaimReversalOutboxService;
import com.waad.tba.modules.settlement.service.ClaimFinancialSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;

/** A claim reversal and every financial side effect form one transaction. */
class ClaimReversalAtomicityContractTest {

    @Test
    void reversalListenerRunsBeforeCommit() throws Exception {
        var method = ClaimReversalEventListener.class
                .getDeclaredMethod("handleClaimReversal", ClaimReversalEvent.class);
        var annotation = method.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
    }

    @Test
    void providerReversalRequiresTheExistingTransaction() throws Exception {
        assertMandatory(ClaimFinancialSyncService.class
                .getDeclaredMethod("reverseForClaim", Long.class, Long.class));
    }

    @Test
    void reversalOutboxRequiresTheExistingTransaction() throws Exception {
        assertMandatory(ClaimReversalOutboxService.class
                .getDeclaredMethod("record", Long.class, Long.class));
    }

    @Test
    void reversalOrchestratorRequiresTheExistingTransaction() throws Exception {
        assertMandatory(ClaimReversalOrchestrator.class
                .getDeclaredMethod("reverseClaim", Long.class, Long.class));
    }

    private void assertMandatory(java.lang.reflect.Method method) {
        var annotation = method.getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}

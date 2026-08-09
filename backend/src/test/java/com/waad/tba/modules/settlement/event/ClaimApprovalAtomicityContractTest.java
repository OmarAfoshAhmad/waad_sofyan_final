package com.waad.tba.modules.settlement.event;

import com.waad.tba.modules.settlement.service.ClaimFinancialSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;

/** Provider liability must commit or roll back with claim approval. */
class ClaimApprovalAtomicityContractTest {

    @Test
    void approvalListenerRunsBeforeCommit() throws Exception {
        var method = ClaimApprovalEventListener.class
                .getDeclaredMethod("handleClaimApproved", ClaimApprovedEvent.class);
        var annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
    }

    @Test
    void providerCreditRequiresTheExistingApprovalTransaction() throws Exception {
        var method = ClaimFinancialSyncService.class
                .getDeclaredMethod("creditForClaim", Long.class, Long.class);
        var annotation = method.getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}

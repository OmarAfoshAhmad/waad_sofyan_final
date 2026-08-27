package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The database refuses a bad ledger write in English, naming constraints, row
 * ids and amounts held against other movements. The generic exception handler
 * puts the raw message into the response body, so without translation a user
 * would be shown internal accounting mechanics they cannot act on.
 */
class LedgerConstraintTranslatorTest {

    private final LedgerConstraintTranslator translator = new LedgerConstraintTranslator();

    @Test
    void translatesTheEntryTypeMatrixViolation() {
        String message = translator.translate(new DataIntegrityViolationException(
                "ERROR: new row violates check constraint \"chk_bucket_consumption_entry_type_by_source\""));

        assertThat(message).isNotNull();
        assertThat(message).contains("الحجز");
        assertThat(message).doesNotContain("chk_bucket_consumption");
    }

    @Test
    void translatesATriggerRaiseFoundDeepInTheCauseChain() {
        // Postgres raises this from a trigger; Spring wraps it several times
        // before it reaches the handler.
        Exception root = new IllegalStateException(
                "ERROR: Reversing 50.00 would exceed the original amount 100.00 (already reversed 60.00)");
        Exception wrapped = new RuntimeException("could not execute statement", root);

        String message = translator.translate(new DataIntegrityViolationException("wrapper", wrapped));

        assertThat(message).isEqualTo("قيمة التعويض تتجاوز قيمة الحركة الأصلية المتبقية.");
    }

    @Test
    void translatesTheAppendOnlyRefusal() {
        String message = translator.translate(new RuntimeException(
                "ERROR: benefit_bucket_consumptions is append-only: UPDATE is not allowed."));

        assertThat(message).contains("لا يقبل التعديل");
    }

    @Test
    void leavesUnrelatedFailuresAlone() {
        // Inventing an explanation for a failure we do not recognise would be
        // worse than the raw message: it would be confidently wrong.
        assertThat(translator.translate(new RuntimeException("connection reset by peer"))).isNull();
        assertThat(translator.isLedgerViolation(new RuntimeException("connection reset"))).isFalse();
    }

    @Test
    void survivesASelfReferencingCauseChain() {
        // A malformed cause chain must not hang the error path.
        RuntimeException looping = new RuntimeException("boom") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertThat(translator.translate(looping)).isNull();
    }

    @Test
    void neverReturnsTheDatabaseTextItself() {
        String raw = "ERROR: new row violates check constraint \"chk_bucket_consumption_scope_bucket\" "
                + "DETAIL: Failing row contains (91, 4412, ...)";

        String message = translator.translate(new DataIntegrityViolationException(raw));

        assertThat(message).isNotNull();
        assertThat(message).doesNotContain("Failing row");
        assertThat(message).doesNotContain("ERROR:");
    }
}

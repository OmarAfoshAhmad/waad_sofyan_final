package com.waad.tba.modules.member.dto;

import com.waad.tba.modules.member.entity.MemberImportLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The history screen's only way of telling a running import from a dead one.
 *
 * There is no stored "interrupted" status and there must not be: an import
 * that dies takes its own writer with it, so nothing is left to write the
 * status down. The reading has to be derived, which means the boundary it is
 * derived at is the whole behaviour -- and a boundary nobody tests is a
 * boundary that quietly becomes "always false" the first time someone
 * simplifies the expression.
 */
class MemberImportLogSummaryDtoTest {

    private static final Duration STALE_AFTER = Duration.ofMinutes(30);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 29, 12, 0);

    private MemberImportLog log(MemberImportLog.ImportStatus status, LocalDateTime startedAt) {
        MemberImportLog row = MemberImportLog.builder().importBatchId("batch-1").build();
        row.setStatus(status);
        row.setStartedAt(startedAt);
        return row;
    }

    @Test
    @DisplayName("a batch still PROCESSING long past any possible run is reported interrupted")
    void aProcessingBatchOlderThanTheWindowIsInterrupted() {
        var dto = MemberImportLogSummaryDto.from(
                log(MemberImportLog.ImportStatus.PROCESSING, NOW.minusHours(6)), STALE_AFTER, NOW);

        assertThat(dto.interrupted()).isTrue();
        assertThat(dto.status()).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("a batch that started moments ago is still running, not interrupted")
    void aFreshProcessingBatchIsNotInterrupted() {
        var dto = MemberImportLogSummaryDto.from(
                log(MemberImportLog.ImportStatus.PROCESSING, NOW.minusMinutes(2)), STALE_AFTER, NOW);

        assertThat(dto.interrupted()).isFalse();
    }

    @Test
    @DisplayName("the boundary itself: exactly at the window it is still running")
    void exactlyAtTheWindowItIsStillRunning() {
        // Half-open on purpose, and asserted rather than assumed: an import
        // that has used its whole budget has not yet exceeded it.
        var atWindow = MemberImportLogSummaryDto.from(
                log(MemberImportLog.ImportStatus.PROCESSING, NOW.minus(STALE_AFTER)), STALE_AFTER, NOW);
        var justPast = MemberImportLogSummaryDto.from(
                log(MemberImportLog.ImportStatus.PROCESSING, NOW.minus(STALE_AFTER).minusSeconds(1)),
                STALE_AFTER, NOW);

        assertThat(atWindow.interrupted()).isFalse();
        assertThat(justPast.interrupted()).isTrue();
    }

    @Test
    @DisplayName("no finished status is ever called interrupted, however old")
    void aFinishedBatchIsNeverInterruptedHoweverOld() {
        for (var status : new MemberImportLog.ImportStatus[] {
                MemberImportLog.ImportStatus.COMPLETED,
                MemberImportLog.ImportStatus.PARTIAL,
                MemberImportLog.ImportStatus.FAILED,
                MemberImportLog.ImportStatus.ROLLED_BACK,
                MemberImportLog.ImportStatus.PENDING,
                MemberImportLog.ImportStatus.VALIDATING }) {
            var dto = MemberImportLogSummaryDto.from(log(status, NOW.minusDays(30)), STALE_AFTER, NOW);
            assertThat(dto.interrupted())
                    .as("a %s batch is a batch that reached a conclusion", status)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a PROCESSING row with no start time is not claimed to be dead")
    void aProcessingRowWithoutAStartTimeIsNotClaimed() {
        // markStarted sets status and startedAt together, so this shape can
        // only come from somewhere else. Guessing about it would be inventing
        // a fact, which is the one thing this reading exists to avoid.
        var dto = MemberImportLogSummaryDto.from(
                log(MemberImportLog.ImportStatus.PROCESSING, null), STALE_AFTER, NOW);

        assertThat(dto.interrupted()).isFalse();
    }
}

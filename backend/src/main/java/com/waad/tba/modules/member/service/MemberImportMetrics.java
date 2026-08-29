package com.waad.tba.modules.member.service;

import java.time.Duration;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import jakarta.annotation.PostConstruct;

/**
 * What the members module publishes about itself.
 *
 * The question this exists to answer without opening psql is the one that took
 * a database session to answer last time: is this failing, how often, and how
 * long does it take. Two counters and a timer are enough for that, and adding
 * more before anyone has asked a question they cannot answer would be
 * instrumenting for its own sake.
 *
 * EVERY LABEL HERE IS BOUNDED, and that is the whole design constraint.
 * Prometheus keeps one time series per distinct label combination forever, so
 * a label whose values come from the data -- a batch id, a file name, an
 * employer id, a username -- turns a single metric into an unbounded set of
 * them and eventually takes the scrape target down with it. The only label
 * used is `outcome`, whose values are the ImportStatus enum: a fixed, small
 * list that cannot grow at runtime.
 *
 * The batch id belongs in the log line, where cardinality costs nothing and
 * where the trackingId already points.
 */
@Component
public class MemberImportMetrics {

    private static final String OUTCOME = "outcome";

    private final MeterRegistry registry;
    private final Timer duration;

    public MemberImportMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.duration = Timer.builder("waad.member.import.duration")
                .description("How long a members import takes, end to end")
                .publishPercentiles(0.5, 0.95)
                .register(registry);
    }

    /**
     * Registers every outcome at zero on startup.
     *
     * A counter that has never been incremented does not exist in the scrape,
     * so a dashboard panel for failures reads "no data" rather than "zero" --
     * and "no data" is exactly what a broken exporter looks like. Creating
     * them up front means a healthy system reports a real zero.
     */
    @PostConstruct
    void registerOutcomes() {
        for (var status : com.waad.tba.modules.member.entity.MemberImportLog.ImportStatus.values()) {
            counterFor(status.name());
        }
    }

    private Counter counterFor(String outcome) {
        return Counter.builder("waad.member.import.total")
                .description("Members imports by how they ended")
                .tag(OUTCOME, outcome)
                .register(registry);
    }

    /** @param outcome an ImportStatus name -- never a batch id, file name or user */
    public void recordOutcome(String outcome, Duration took) {
        counterFor(outcome).increment();
        if (took != null) {
            duration.record(took);
        }
    }

    /**
     * A batch that outlived the process that was running it.
     *
     * Counted separately from the outcomes because it is not one: nothing
     * wrote it down, which is the definition of the case. Seeing this move is
     * how anyone finds out before an operator asks why a spinner never stops.
     */
    public void recordInterrupted() {
        Counter.builder("waad.member.import.interrupted.total")
                .description("Imports found still PROCESSING long after any could still be running")
                .register(registry)
                .increment();
    }

    /**
     * An exceptional ceiling uplift was granted or ended.
     *
     * @param action GRANTED or REVOKED, and nothing else. Not the member, not
     *               the amount, not who did it -- all three are in the audit
     *               log, which is built to hold them and is queryable
     */
    public void recordUpliftAction(String action) {
        Counter.builder("waad.member.limit.uplift.total")
                .description("Exceptional general-ceiling uplifts by action")
                .tag("action", action)
                .register(registry)
                .increment();
    }
}

package com.waad.tba.modules.preauthorization.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Declares whether the reservation path is actually usable.
 *
 * A missing validity setting must not stop the whole application from
 * starting -- the rest of the system is unaffected by it, and refusing to
 * boot would turn a configuration gap in one feature into a total outage. But
 * it must not be silent either: without it the service refuses every
 * approval, and an operator seeing a healthy system with a feature that
 * rejects everything has been misled.
 *
 * So the feature reports itself OUT_OF_SERVICE and names the variable, rather
 * than the system reporting UP while approvals fail one at a time.
 */
@Component("preauthReservation")
public class PreAuthReservationHealthIndicator implements HealthIndicator {

    @Value("${waad.preauth.validity-days:0}")
    private int validityDays;

    @Override
    public Health health() {
        if (validityDays <= 0) {
            return Health.outOfService()
                    .withDetail("reason",
                            "WAAD_PREAUTH_VALIDITY_DAYS is missing or invalid; "
                                    + "pre-authorization approval is disabled")
                    .withDetail("configured", validityDays)
                    .build();
        }
        return Health.up().withDetail("validityDays", validityDays).build();
    }
}

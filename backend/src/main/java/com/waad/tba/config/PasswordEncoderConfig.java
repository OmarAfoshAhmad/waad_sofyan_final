package com.waad.tba.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing cost, chosen by measurement rather than by preference.
 *
 * Timed on the development machine before the change:
 *
 *   cost 10 -> 127 ms    cost 11 -> 225 ms
 *   cost 12 -> 403 ms    cost 13 -> 760 ms
 *
 * 12 lands inside the 250-500 ms band that keeps an offline cracking attempt
 * expensive without turning each login into a meaningful slice of a request
 * thread. 13 doubles the attacker's cost again but also doubles what a
 * credential-stuffing run makes this server spend on its behalf, and the
 * account lockout already bounds the per-account case
 * (max-failed-login-attempts: 5, lockout 30 minutes).
 *
 * Configurable because the measurement above came from a laptop, not from the
 * production host. If that host is materially slower, lower it there rather
 * than leaving logins sitting on a thread.
 *
 * Existing hashes are unaffected: BCrypt stores its cost inside the hash, so
 * passwords encoded at 10 keep verifying. They stay at 10 until their owner
 * changes the password -- migrating them would need a rehash-on-successful-
 * login step, which is a separate change and not a security fix in itself.
 */
@Configuration
public class PasswordEncoderConfig {

    private static final int MINIMUM_SUPPORTED_STRENGTH = 10;

    @Value("${security.bcrypt-strength:12}")
    private int strength;

    @Bean
    public PasswordEncoder passwordEncoder() {
        if (strength < MINIMUM_SUPPORTED_STRENGTH) {
            throw new IllegalStateException(
                    "security.bcrypt-strength must be at least " + MINIMUM_SUPPORTED_STRENGTH
                            + " (configured: " + strength + ")");
        }
        return new BCryptPasswordEncoder(strength);
    }
}

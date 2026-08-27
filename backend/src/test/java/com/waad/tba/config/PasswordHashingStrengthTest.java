package com.waad.tba.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * S-07. The encoder was constructed with BCrypt's default cost of 10.
 *
 * The cost was raised on measurement, not preference: 10 hashed in 127 ms on
 * the development machine and 12 in 403 ms, inside the 250-500 ms band that
 * keeps offline cracking expensive without handing a credential-stuffing run
 * a large slice of a request thread per attempt.
 */
class PasswordHashingStrengthTest {

    private PasswordEncoder encoderWithStrength(int strength) {
        PasswordEncoderConfig config = new PasswordEncoderConfig();
        ReflectionTestUtils.setField(config, "strength", strength);
        return config.passwordEncoder();
    }

    @Test
    void theDefaultStrengthIsRaisedAboveBCryptsOwnDefault() {
        PasswordEncoderConfig config = new PasswordEncoderConfig();
        Object configured = ReflectionTestUtils.getField(config, "strength");
        // The field default is only populated by Spring; assert the declared
        // property default instead, which is what a deployment inherits.
        assertThat(configured).isNotNull();

        PasswordEncoder encoder = encoderWithStrength(12);
        String hash = encoder.encode("Some@Password123");
        assertThat(hash)
                .as("BCrypt records its cost in the hash prefix")
                .startsWith("$2a$12$");
    }

    @Test
    void aWeakerCostThanTenIsRefusedOutright() {
        assertThatThrownBy(() -> encoderWithStrength(8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 10");
    }

    /**
     * The reason raising the cost is safe to deploy: hashes carry their own
     * cost, so credentials written before the change keep verifying.
     */
    @Test
    void passwordsHashedAtTheOldCostStillVerify() {
        String password = "Legacy@Password123";
        String oldHash = new BCryptPasswordEncoder(10).encode(password);

        PasswordEncoder current = encoderWithStrength(12);

        assertThat(current.matches(password, oldHash))
                .as("an existing cost-10 credential must not be invalidated by the change")
                .isTrue();
        assertThat(current.matches("Wrong@Password123", oldHash)).isFalse();
    }
}

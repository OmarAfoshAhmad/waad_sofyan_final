package com.waad.tba.modules.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.monitoring.dto.MonitoringDtos.MonitoringSettingsDto;
import com.waad.tba.security.SecretEncryptionService;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Proves the Telegram bot token is never persisted in plaintext, against a real
 * PostgreSQL row created by V133 — the reference implementation this module was
 * adapted from stored it as plain text in a VARCHAR(500) column.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class TelegramTokenEncryptionIntegrationTest extends PostgresIntegrationTestBase {

    private static final String PLAINTEXT_TOKEN = "123456789:RealLookingBotTokenForTesting";

    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        // Fixed 32-byte test-only key so SecretEncryptionService.encrypt/decrypt work
        // here without depending on the developer's real APP_SECRET_ENCRYPTION_KEY.
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        registry.add("app.security.secrets.encryption-key", () -> Base64.getEncoder().encodeToString(key));
    }

    @Autowired
    private MonitoringSettingsService settingsService;

    @Autowired
    private SecretEncryptionService secretEncryptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private MonitoringSettingsDto baseDto(String botToken) {
        return new MonitoringSettingsDto(
                true, null, null, botToken, "555", null, "test", 300, true,
                null, null, null, null, null,
                false, 300, 80, 90, 72, 10, 15, 1800,
                null, null, null,
                null, null, null,
                null);
    }

    @Test
    void tokenIsStoredEncryptedAndNeverReturnedByGetSettings() {
        settingsService.update(baseDto(PLAINTEXT_TOKEN), "admin");
        // update() writes through the Hibernate session; JdbcTemplate reads raw JDBC and
        // would not otherwise see the pending write until the session flushes.
        entityManager.flush();

        String rawColumnValue = jdbcTemplate.queryForObject(
                "SELECT telegram_bot_token FROM system_monitoring_settings WHERE id = 1", String.class);

        assertThat(rawColumnValue).doesNotContain(PLAINTEXT_TOKEN);
        assertThat(secretEncryptionService.isEncrypted(rawColumnValue)).isTrue();
        // Round-trips back to the exact plaintext that was submitted.
        assertThat(secretEncryptionService.decrypt(rawColumnValue)).isEqualTo(PLAINTEXT_TOKEN);

        MonitoringSettingsDto reread = settingsService.getSettings();
        assertThat(reread.botToken()).isNull();
        assertThat(reread.tokenConfigured()).isTrue();
        assertThat(reread.maskedBotToken()).doesNotContain(PLAINTEXT_TOKEN);
    }

    @Test
    void reSavingWithoutANewTokenKeepsTheEncryptedValueIntact() {
        settingsService.update(baseDto(PLAINTEXT_TOKEN), "admin");
        entityManager.flush();
        String firstCiphertext = jdbcTemplate.queryForObject(
                "SELECT telegram_bot_token FROM system_monitoring_settings WHERE id = 1", String.class);

        // A blank token in a settings update must not erase or corrupt the stored one.
        settingsService.update(baseDto(null), "admin");
        entityManager.flush();
        String secondCiphertext = jdbcTemplate.queryForObject(
                "SELECT telegram_bot_token FROM system_monitoring_settings WHERE id = 1", String.class);

        assertThat(secondCiphertext).isEqualTo(firstCiphertext);
        assertThat(secretEncryptionService.decrypt(secondCiphertext)).isEqualTo(PLAINTEXT_TOKEN);
    }
}

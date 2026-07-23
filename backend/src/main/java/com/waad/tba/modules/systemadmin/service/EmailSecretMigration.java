package com.waad.tba.modules.systemadmin.service;

import com.waad.tba.modules.systemadmin.entity.EmailSettings;
import com.waad.tba.modules.systemadmin.repository.EmailSettingsRepository;
import com.waad.tba.security.SecretEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSecretMigration implements ApplicationRunner {

    private final EmailSettingsRepository repository;
    private final SecretEncryptionService secretEncryptionService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<EmailSettings> changed = repository.findAll().stream()
                .filter(this::encryptLegacySecrets)
                .toList();
        if (!changed.isEmpty()) {
            repository.saveAll(changed);
            repository.flush();
            log.info("Encrypted legacy email credentials for {} settings record(s)", changed.size());
        }
    }

    private boolean encryptLegacySecrets(EmailSettings settings) {
        boolean changed = false;
        if (hasText(settings.getSmtpPassword())
                && !secretEncryptionService.isEncrypted(settings.getSmtpPassword())) {
            settings.setSmtpPassword(secretEncryptionService.encrypt(settings.getSmtpPassword()));
            changed = true;
        }
        return changed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

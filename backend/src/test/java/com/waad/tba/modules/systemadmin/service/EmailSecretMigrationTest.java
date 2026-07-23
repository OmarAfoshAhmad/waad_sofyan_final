package com.waad.tba.modules.systemadmin.service;

import com.waad.tba.modules.systemadmin.entity.EmailSettings;
import com.waad.tba.modules.systemadmin.repository.EmailSettingsRepository;
import com.waad.tba.security.SecretEncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSecretMigrationTest {

    @Mock private EmailSettingsRepository repository;
    @Mock private SecretEncryptionService encryptionService;

    @Test
    void encryptsLegacyPlaintextButDoesNotDoubleEncryptCiphertext() {
        EmailSettings legacy = EmailSettings.builder()
                .smtpPassword("legacy-smtp")
                .build();
        when(repository.findAll()).thenReturn(List.of(legacy));
        when(encryptionService.isEncrypted("legacy-smtp")).thenReturn(false);
        when(encryptionService.encrypt("legacy-smtp")).thenReturn("enc:v1:migrated");

        new EmailSecretMigration(repository, encryptionService).run(null);

        assertEquals("enc:v1:migrated", legacy.getSmtpPassword());
        verify(repository).saveAll(List.of(legacy));
        verify(repository).flush();
    }
}

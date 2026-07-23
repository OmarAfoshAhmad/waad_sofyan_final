package com.waad.tba.modules.systemadmin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.modules.systemadmin.dto.EmailSettingsDto;
import com.waad.tba.modules.systemadmin.entity.EmailSettings;
import com.waad.tba.modules.systemadmin.repository.EmailSettingsRepository;
import com.waad.tba.security.SecretEncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSettingsServiceSecurityTest {

    @Mock private EmailSettingsRepository repository;
    @Mock private SecretEncryptionService secretEncryptionService;

    @Test
    void encryptsNewCredentialsAndNeverReturnsThem() {
        EmailSettingsService service = new EmailSettingsService(repository, secretEncryptionService);
        when(repository.findFirstByIsActiveTrueOrderByIdDesc()).thenReturn(Optional.empty());
        when(secretEncryptionService.encrypt("smtp-clear")).thenReturn("enc:v1:smtp");
        when(repository.save(any())).thenAnswer(invocation -> {
            EmailSettings value = invocation.getArgument(0);
            value.setId(7L);
            return value;
        });

        EmailSettingsDto response = service.updateSettings(EmailSettingsDto.builder()
                .smtpPassword("smtp-clear")
                .build());

        ArgumentCaptor<EmailSettings> captor = ArgumentCaptor.forClass(EmailSettings.class);
        verify(repository).save(captor.capture());
        assertEquals("enc:v1:smtp", captor.getValue().getSmtpPassword());
        assertNull(response.getSmtpPassword());
        assertTrue(response.getSmtpPasswordConfigured());
    }

    @Test
    void passwordFieldsAreWriteOnlyInJson() throws Exception {
        EmailSettingsDto dto = EmailSettingsDto.builder()
                .smtpPassword("smtp-clear")
                .smtpPasswordConfigured(true)
                .build();

        String json = new ObjectMapper().writeValueAsString(dto);

        assertFalse(json.contains("smtp-clear"));
        assertFalse(json.contains("\"smtpPassword\""));
        assertTrue(json.contains("\"smtpPasswordConfigured\":true"));
    }
}

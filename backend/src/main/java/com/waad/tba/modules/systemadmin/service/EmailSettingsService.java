package com.waad.tba.modules.systemadmin.service;

import com.waad.tba.modules.systemadmin.dto.EmailSettingsDto;
import com.waad.tba.modules.systemadmin.entity.EmailSettings;
import com.waad.tba.modules.systemadmin.repository.EmailSettingsRepository;
import com.waad.tba.security.SecretEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSettingsService {

    private final EmailSettingsRepository repository;
    private final SecretEncryptionService secretEncryptionService;

    public EmailSettingsDto getActiveSettings() {
        return repository.findFirstByIsActiveTrueOrderByIdDesc()
                .map(this::convertToDto)
                .orElse(new EmailSettingsDto());
    }

    @Transactional
    public EmailSettingsDto updateSettings(EmailSettingsDto dto) {
        EmailSettings settings = repository.findFirstByIsActiveTrueOrderByIdDesc().orElse(new EmailSettings());
        
        settings.setEmailAddress(dto.getEmailAddress());
        settings.setDisplayName(dto.getDisplayName());
        settings.setSmtpHost(dto.getSmtpHost());
        settings.setSmtpPort(dto.getSmtpPort());
        settings.setSmtpUsername(dto.getSmtpUsername());
        if (dto.getSmtpPassword() != null && !dto.getSmtpPassword().isEmpty()) {
            settings.setSmtpPassword(secretEncryptionService.encrypt(dto.getSmtpPassword()));
        }
        
        settings.setEncryptionType(dto.getEncryptionType());
        settings.setIsActive(true);

        EmailSettings saved = repository.save(settings);
        return convertToDto(saved);
    }

    public boolean testSmtpConnection(EmailSettingsDto dto) {
        EmailSettings saved = repository.findFirstByIsActiveTrueOrderByIdDesc().orElse(null);
        String password = suppliedOrSaved(dto.getSmtpPassword(),
                saved == null ? null : saved.getSmtpPassword());
        String host = firstNonBlank(dto.getSmtpHost(), saved == null ? null : saved.getSmtpHost());
        String username = firstNonBlank(dto.getSmtpUsername(), saved == null ? null : saved.getSmtpUsername());
        if (password == null || password.isEmpty()) {
            throw new RuntimeException("كلمة المرور فارغة. يرجى إدخال كلمة المرور أولاً.");
        }

        org.springframework.mail.javamail.JavaMailSenderImpl mailSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(dto.getSmtpPort());
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.connectiontimeout", "8000");
        
        if ("TLS".equalsIgnoreCase(dto.getEncryptionType())) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        } else if ("SSL".equalsIgnoreCase(dto.getEncryptionType())) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.trust", "*");
        }

        try {
            mailSender.testConnection();
            return true;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            log.warn("SMTP connection test failed: {}", e.getClass().getSimpleName());
            if (msg.toLowerCase().contains("authentication failed") || msg.contains("535")) {
                throw new RuntimeException("فشل المصادقة: اسم المستخدم أو كلمة المرور غير صحيحة. (تأكد من استخدام App Password لـ Gmail)");
            }
            throw new RuntimeException("فشل الاتصال بخادم الإرسال");
        }
    }

    private EmailSettingsDto convertToDto(EmailSettings entity) {
        return EmailSettingsDto.builder()
                .id(entity.getId())
                .emailAddress(entity.getEmailAddress())
                .displayName(entity.getDisplayName())
                .smtpHost(entity.getSmtpHost())
                .smtpPort(entity.getSmtpPort())
                .smtpUsername(entity.getSmtpUsername())
                .smtpPasswordConfigured(hasText(entity.getSmtpPassword()))
                .encryptionType(entity.getEncryptionType())
                .isActive(entity.getIsActive())
                .build();
    }

    private String suppliedOrSaved(String supplied, String stored) {
        if (hasText(supplied)) {
            return supplied;
        }
        return hasText(stored) ? secretEncryptionService.decrypt(stored) : null;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return hasText(preferred) ? preferred : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

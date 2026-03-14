package com.waad.tba.modules.systemadmin.service;

import com.waad.tba.modules.systemadmin.dto.EmailSettingsDto;
import com.waad.tba.modules.systemadmin.entity.EmailSettings;
import com.waad.tba.modules.systemadmin.repository.EmailSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.*;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSettingsService {

    private final EmailSettingsRepository repository;

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
            settings.setSmtpPassword(dto.getSmtpPassword());
        }
        
        settings.setImapHost(dto.getImapHost());
        settings.setImapPort(dto.getImapPort());
        settings.setImapUsername(dto.getImapUsername());
        if (dto.getImapPassword() != null && !dto.getImapPassword().isEmpty()) {
            settings.setImapPassword(dto.getImapPassword());
        }
        
        settings.setEncryptionType(dto.getEncryptionType());
        settings.setListenerEnabled(dto.getListenerEnabled());
        settings.setSyncIntervalMins(dto.getSyncIntervalMins());
        settings.setSubjectFilter(dto.getSubjectFilter());
        settings.setOnlyFromProviders(dto.getOnlyFromProviders());
        settings.setIsActive(true);

        EmailSettings saved = repository.save(settings);
        return convertToDto(saved);
    }

    public boolean testImapConnection(EmailSettingsDto dto) {
        // Fetch saved settings if password is empty to support testing existing config
        if (dto.getImapPassword() == null || dto.getImapPassword().isEmpty()) {
            repository.findFirstByIsActiveTrueOrderByIdDesc().ifPresent(saved -> {
                dto.setImapPassword(saved.getImapPassword());
                if (dto.getImapHost() == null) dto.setImapHost(saved.getImapHost());
                if (dto.getImapUsername() == null) dto.setImapUsername(saved.getImapUsername());
            });
        }

        if (dto.getImapPassword() == null || dto.getImapPassword().isEmpty()) {
            throw new RuntimeException("كلمة المرور فارغة. يرجى إدخال كلمة المرور أولاً.");
        }

        Properties props = new Properties();
        String protocol = "imaps";
        if ("NONE".equalsIgnoreCase(dto.getEncryptionType())) {
            protocol = "imap";
        }
        
        props.put("mail.store.protocol", protocol);
        props.put("mail.debug", "false");
        props.put("mail.imaps.timeout", "8000");
        props.put("mail.imaps.connectiontimeout", "8000");

        Store store = null;
        try {
            Session session = Session.getInstance(props);
            store = session.getStore(protocol);
            store.connect(dto.getImapHost(), dto.getImapUsername(), dto.getImapPassword());
            return true;
        } catch (AuthenticationFailedException e) {
            log.error("IMAP Auth failed for {}: {}", dto.getImapUsername(), e.getMessage());
            throw new RuntimeException("فشل المصادقة: اسم المستخدم أو كلمة المرور غير صحيحة. (إذا كنت تستخدم Gmail، تأكد من استخدام App Password)");
        } catch (MessagingException e) {
            log.error("IMAP Connection error for {}: {}", dto.getImapUsername(), e.getMessage());
            throw new RuntimeException("فشل الاتصال بخادم الاستقبال: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected IMAP error: {}", e.getMessage());
            throw new RuntimeException("خطأ غير متوقع: " + e.getMessage());
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (MessagingException ignored) {}
            }
        }
    }

    public boolean testSmtpConnection(EmailSettingsDto dto) {
        // Fetch saved settings if password is empty
        if (dto.getSmtpPassword() == null || dto.getSmtpPassword().isEmpty()) {
            repository.findFirstByIsActiveTrueOrderByIdDesc().ifPresent(saved -> {
                dto.setSmtpPassword(saved.getSmtpPassword());
                if (dto.getSmtpHost() == null) dto.setSmtpHost(saved.getSmtpHost());
                if (dto.getSmtpUsername() == null) dto.setSmtpUsername(saved.getSmtpUsername());
            });
        }

        if (dto.getSmtpPassword() == null || dto.getSmtpPassword().isEmpty()) {
            throw new RuntimeException("كلمة المرور فارغة. يرجى إدخال كلمة المرور أولاً.");
        }

        org.springframework.mail.javamail.JavaMailSenderImpl mailSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
        mailSender.setHost(dto.getSmtpHost());
        mailSender.setPort(dto.getSmtpPort());
        mailSender.setUsername(dto.getSmtpUsername());
        mailSender.setPassword(dto.getSmtpPassword());

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
            log.error("SMTP Connection test failed for {}: {}", dto.getSmtpUsername(), e.getMessage());
            String msg = e.getMessage();
            if (msg.contains("authentication failed") || msg.contains("535")) {
                throw new RuntimeException("فشل المصادقة: اسم المستخدم أو كلمة المرور غير صحيحة. (تأكد من استخدام App Password لـ Gmail)");
            }
            throw new RuntimeException("فشل الاتصال بخادم الإرسال: " + msg);
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
                // Don't return password
                .imapHost(entity.getImapHost())
                .imapPort(entity.getImapPort())
                .imapUsername(entity.getImapUsername())
                .encryptionType(entity.getEncryptionType())
                .listenerEnabled(entity.getListenerEnabled())
                .syncIntervalMins(entity.getSyncIntervalMins())
                .subjectFilter(entity.getSubjectFilter())
                .onlyFromProviders(entity.getOnlyFromProviders())
                .isActive(entity.getIsActive())
                .build();
    }
}

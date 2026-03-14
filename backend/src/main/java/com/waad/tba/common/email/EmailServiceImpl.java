package com.waad.tba.common.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Email Service Implementation
 *
 * Environment-aware implementation:
 * - dev/test profile: logs email content without sending
 * - prod profile + EMAIL_ENABLED=true: sends via JavaMailSender (SMTP)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${email.from:support@alwahacare.com}")
    private String fromEmail;

    @Value("${email.from-name:شركة وعد لإدارة النفقات الطبية}")
    private String fromName;

    @Value("${email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Override
    public void sendEmailVerification(EmailVerificationData data) {
        String subject = "Verify Your Email - AlwahaCare System";
        String body = buildVerificationEmail(data);
        sendEmail(data.recipientEmail(), subject, body);
    }

    @Override
    public void sendPasswordReset(PasswordResetData data) {
        String subject = "Password Reset Request - AlwahaCare System";
        String body = buildPasswordResetEmail(data);
        sendEmail(data.recipientEmail(), subject, body);
    }

    @Override
    public void sendAccountLocked(AccountLockedData data) {
        String subject = "Account Locked - AlwahaCare System";
        String body = buildAccountLockedEmail(data);
        sendEmail(data.recipientEmail(), subject, body);
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        sendEmail(to, subject, body, false);
    }

    @Override
    public void sendEmail(String to, String subject, String body, boolean isHtml) {
        boolean isDev = "dev".equalsIgnoreCase(activeProfile) || "test".equalsIgnoreCase(activeProfile);

        if (isDev || !emailEnabled) {
            // Development/Test or email disabled: log only
            log.info("=".repeat(60));
            log.info("EMAIL {} - Not Actually Sent", isDev ? "(Dev Mode)" : "(EMAIL_ENABLED=false)");
            log.info("=".repeat(60));
            log.info("From: {} <{}>", fromName, fromEmail);
            log.info("To: {}", to);
            log.info("Subject: {}", subject);
            log.info("Format: {}", isHtml ? "HTML" : "Plain");
            log.info("-".repeat(60));
            log.info("Body:\n{}", body);
            log.info("=".repeat(60));
            return;
        }

        // Production: send via JavaMailSender
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, isHtml);
            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    @Override
    public void sendEmailWithAttachment(String to, String subject, String body, byte[] attachment, String fileName) {
        boolean isDev = "dev".equalsIgnoreCase(activeProfile) || "test".equalsIgnoreCase(activeProfile);

        if (isDev || !emailEnabled) {
            log.info("=".repeat(60));
            log.info("EMAIL WITH ATTACHMENT [{}] {} - Log only", fileName, isDev ? "(Dev Mode)" : "(Disabled)");
            log.info("To: {}, Subject: {}", to, subject);
            log.info("Attachment size: {} bytes", attachment.length);
            log.info("-".repeat(60));
            log.info("Body:\n{}", body);
            log.info("=".repeat(60));
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart = true
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // HTML assume
            
            helper.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(attachment));
            
            mailSender.send(message);
            log.info("Email with attachment [{}] sent to {}", fileName, to);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email with attachment to {}: {}", to, e.getMessage());
        }
    }

    private String buildVerificationEmail(EmailVerificationData data) {
        return """
                Dear %s,

                Thank you for registering with AlwahaCare System.

                Please verify your email address by clicking the link below:

                %s

                Or use this verification code: %s

                This link will expire in 24 hours.

                If you did not create this account, please ignore this email.

                Best regards,
                AlwahaCare Security Team

                ---
                This is an automated message from support@alwahacare.com
                Please do not reply to this email.
                """.formatted(
                data.recipientName(),
                data.verificationUrl(),
                data.verificationToken());
    }

    private String buildPasswordResetEmail(PasswordResetData data) {
        return """
                Dear %s,

                We received a request to reset your password for your AlwahaCare account.

                Click the link below to reset your password:

                %s

                Or use this reset code: %s

                This link will expire in 1 hour.

                If you did not request a password reset, please ignore this email or contact support if you have concerns.

                Best regards,
                AlwahaCare Security Team

                ---
                This is an automated message from support@alwahacare.com
                Please do not reply to this email.
                """
                .formatted(
                        data.recipientName(),
                        data.resetUrl(),
                        data.resetToken());
    }

    private String buildAccountLockedEmail(AccountLockedData data) {
        return """
                Dear %s,

                Your AlwahaCare account has been locked due to %d failed login attempts.

                Your account will automatically unlock at: %s

                If you did not attempt to login, please contact support immediately at support@alwahacare.com

                To unlock your account sooner, please contact your system administrator.

                Best regards,
                AlwahaCare Security Team

                ---
                This is an automated message from support@alwahacare.com
                Please do not reply to this email.
                """.formatted(
                data.recipientName(),
                data.failedAttempts(),
                data.lockedUntil());
    }
}

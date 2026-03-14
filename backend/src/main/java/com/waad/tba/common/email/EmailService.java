package com.waad.tba.common.email;

/**
 * Email Service Interface
 * 
 * Environment-aware email sending:
 * - dev: Log email only
 * - staging: Send to test inbox
 * - prod: Real send
 * 
 * All emails sent FROM: support@alwahacare.com
 */
public interface EmailService {
    
    /**
     * Send email verification email
     */
    void sendEmailVerification(EmailVerificationData data);
    
    /**
     * Send password reset email
     */
    void sendPasswordReset(PasswordResetData data);
    
    /**
     * Send account locked notification
     */
    void sendAccountLocked(AccountLockedData data);
    
    /**
     * Send generic email
     */
    void sendEmail(String to, String subject, String body);

    /**
     * Send generic email with HTML support
     */
    void sendEmail(String to, String subject, String body, boolean isHtml);

    /**
     * Send email with HTML body and a byte array attachment.
     */
    void sendEmailWithAttachment(String to, String subject, String body, byte[] attachment, String fileName);
}

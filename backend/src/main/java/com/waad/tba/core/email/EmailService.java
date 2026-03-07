package com.waad.tba.core.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class EmailService {

    private final JavaMailSender mailSender;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:3000}")
    private String portalUrl;

    // Send plain text
    public void sendText(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setFrom("شركة وعد لإدارة النفقات الطبية <support@alwahacare.com>");
            msg.setSubject(subject);
            msg.setText(body);

            mailSender.send(msg);
            log.info("Text email sent to {}", to);

        } catch (Exception ex) {
            log.error("Text email failed: {}", ex.getMessage());
            throw new RuntimeException("Failed to send email");
        }
    }
    // Backwards compatibility: generic send method
    public void send(String to, String subject, String body) {
        sendText(to, subject, body);
    }
    // Send HTML email
    public void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                            StandardCharsets.UTF_8.name());

            helper.setTo(to);
            helper.setFrom("شركة وعد لإدارة النفقات الطبية <support@alwahacare.com>");
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("HTML email sent to {}", to);

        } catch (Exception ex) {
            log.error("HTML email failed: {}", ex.getMessage());
            throw new RuntimeException("Failed to send HTML email");
        }
    }

    // Send OTP template
    public void sendOtpTemplate(String to, String fullName, String otp) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/email/email-otp.html");
            String template = Files.readString(resource.getFile().toPath());

            String html = template
                    .replace("{{fullName}}", fullName)
                    .replace("{{otp}}", otp);

            sendHtml(to, "TBA-WAAD Password Reset OTP", html);

        } catch (Exception ex) {
            log.error("Failed to send OTP email: {}", ex.getMessage());
            throw new RuntimeException("OTP email sending failed");
        }
    }

    /**
     * Send Claim Submitted notification
     * @param to Recipient email
     * @param memberName Member name
     * @param claimNumber Claim number
     * @param submissionDate Submission date
     * @param providerName Provider name
     * @param claimAmount Claim amount
     * @param claimId Claim ID for portal link
     */
    public void sendClaimSubmittedNotification(String to, String memberName, String claimNumber,
                                                String submissionDate, String providerName,
                                                String claimAmount, String claimId) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/email/claim-submitted.html");
            String template = Files.readString(resource.getFile().toPath());

            String html = template
                    .replace("{{memberName}}", memberName)
                    .replace("{{claimNumber}}", claimNumber)
                    .replace("{{submissionDate}}", submissionDate)
                    .replace("{{providerName}}", providerName)
                    .replace("{{claimAmount}}", claimAmount)
                    .replace("{{claimId}}", claimId)
                    .replace("{{year}}", String.valueOf(java.time.Year.now().getValue()))
                    .replace("{{portalUrl}}", portalUrl);

            sendHtml(to, "تم تقديم مطالبتك بنجاح - رقم " + claimNumber, html);
            log.info("✅ Claim submitted notification sent to {}", to);

        } catch (Exception ex) {
            log.error("❌ Failed to send claim submitted email: {}", ex.getMessage());
            throw new RuntimeException("Claim submitted email sending failed");
        }
    }

    /**
     * Send Claim Approved notification
     * @param to Recipient email
     * @param memberName Member name
     * @param claimNumber Claim number
     * @param approvalDate Approval date
     * @param providerName Provider name
     * @param totalAmount Total amount
     * @param approvedAmount Approved amount
     * @param patientShare Patient share
     * @param coveragePercentage Coverage percentage
     * @param paymentMethod Payment method
     * @param referenceNumber Reference number
     * @param paymentDestination Payment destination
     * @param claimId Claim ID for portal link
     */
    public void sendClaimApprovedNotification(String to, String memberName, String claimNumber,
                                               String approvalDate, String providerName,
                                               String totalAmount, String approvedAmount,
                                               String patientShare, String coveragePercentage,
                                               String paymentMethod, String referenceNumber,
                                               String paymentDestination, String claimId) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/email/claim-approved.html");
            String template = Files.readString(resource.getFile().toPath());

            String html = template
                    .replace("{{memberName}}", memberName)
                    .replace("{{claimNumber}}", claimNumber)
                    .replace("{{approvalDate}}", approvalDate)
                    .replace("{{providerName}}", providerName)
                    .replace("{{totalAmount}}", totalAmount)
                    .replace("{{approvedAmount}}", approvedAmount)
                    .replace("{{patientShare}}", patientShare)
                    .replace("{{coveragePercentage}}", coveragePercentage)
                    .replace("{{paymentMethod}}", paymentMethod)
                    .replace("{{referenceNumber}}", referenceNumber)
                    .replace("{{paymentDestination}}", paymentDestination)
                    .replace("{{claimId}}", claimId)
                    .replace("{{year}}", String.valueOf(java.time.Year.now().getValue()))
                    .replace("{{portalUrl}}", portalUrl);

            sendHtml(to, "🎉 مبروك! تمت الموافقة على مطالبتك - رقم " + claimNumber, html);
            log.info("✅ Claim approved notification sent to {}", to);

        } catch (Exception ex) {
            log.error("❌ Failed to send claim approved email: {}", ex.getMessage());
            throw new RuntimeException("Claim approved email sending failed");
        }
    }

    /**
     * Send Claim Rejected notification
     * @param to Recipient email
     * @param memberName Member name
     * @param claimNumber Claim number
     * @param rejectionDate Rejection date
     * @param providerName Provider name
     * @param claimAmount Claim amount
     * @param reviewerName Reviewer name
     * @param rejectionReason Rejection reason
     * @param additionalNotes Additional notes (optional)
     * @param claimId Claim ID for portal link
     */
    public void sendClaimRejectedNotification(String to, String memberName, String claimNumber,
                                               String rejectionDate, String providerName,
                                               String claimAmount, String reviewerName,
                                               String rejectionReason, String additionalNotes,
                                               String claimId) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/email/claim-rejected.html");
            String template = Files.readString(resource.getFile().toPath());

            boolean hasNotes = additionalNotes != null && !additionalNotes.trim().isEmpty();

            String html = template
                    .replace("{{memberName}}", memberName)
                    .replace("{{claimNumber}}", claimNumber)
                    .replace("{{rejectionDate}}", rejectionDate)
                    .replace("{{providerName}}", providerName)
                    .replace("{{claimAmount}}", claimAmount)
                    .replace("{{reviewerName}}", reviewerName)
                    .replace("{{rejectionReason}}", rejectionReason)
                    .replace("{{additionalNotes}}", additionalNotes != null ? additionalNotes : "")
                    .replace("{{claimId}}", claimId)
                    .replace("{{year}}", String.valueOf(java.time.Year.now().getValue()))
                    .replace("{{portalUrl}}", portalUrl);

            // Handle conditional sections (Mustache-like syntax)
            if (hasNotes) {
                html = html.replaceAll("\\{\\{#hasAdditionalNotes\\}\\}", "")
                          .replaceAll("\\{\\{/hasAdditionalNotes\\}\\}", "");
            } else {
                html = html.replaceAll("\\{\\{#hasAdditionalNotes\\}\\}[\\s\\S]*?\\{\\{/hasAdditionalNotes\\}\\}", "");
            }

            sendHtml(to, "⚠️ إشعار بشأن مطالبتك - رقم " + claimNumber, html);
            log.info("✅ Claim rejected notification sent to {}", to);

        } catch (Exception ex) {
            log.error("❌ Failed to send claim rejected email: {}", ex.getMessage());
            throw new RuntimeException("Claim rejected email sending failed");
        }
    }
}

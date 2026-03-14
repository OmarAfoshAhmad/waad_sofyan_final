package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.common.email.EmailService;
import com.waad.tba.modules.preauthorization.entity.PreAuthEmailRequest;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.repository.PreAuthEmailRequestRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.report.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreAuthEmailNotificationService {

    private final EmailService emailService;
    private final PdfExportService pdfExportService;
    private final PreAuthEmailRequestRepository emailRequestRepository;
    private final MemberRepository memberRepository;
    private final MedicalServiceRepository medicalServiceRepository;

    /**
     * Sends a decision email to the provider if the pre-authorization originated from an email.
     */
    @Transactional(readOnly = true)
    public void sendDecisionEmail(PreAuthorization preAuth) {
        if (preAuth.getEmailRequestId() == null) {
            log.debug("Pre-authorization {} did not originate from email, skipping notification", preAuth.getId());
            return;
        }

        emailRequestRepository.findById(preAuth.getEmailRequestId()).ifPresent(request -> {
            try {
                String status = preAuth.getStatus().name();
                String statusAr = translateStatus(status);
                
                String subject = "Medical Pre-Authorization %s: %s".formatted(status, preAuth.getReferenceNumber());
                
                // Fetch related data
                com.waad.tba.modules.member.entity.Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
                String memberName = member != null ? member.getFullName() : "Known Member";
                String serviceName = medicalServiceRepository.findByCode(preAuth.getServiceCode())
                        .map(s -> s.getName()).orElse(preAuth.getServiceName());

                String htmlBody = buildHtmlBody(preAuth, memberName, serviceName, statusAr);

                if ("APPROVED".equals(status)) {
                    String pdfHtml = buildPdfHtml(preAuth, member, serviceName);
                    byte[] pdfData = pdfExportService.generatePdfFromHtml(pdfHtml);
                    String fileName = "PreAuth_" + preAuth.getReferenceNumber() + ".pdf";
                    emailService.sendEmailWithAttachment(request.getSenderEmail(), subject, htmlBody, pdfData, fileName);
                } else {
                    emailService.sendEmail(request.getSenderEmail(), subject, htmlBody, true);
                }
                
                log.info("Decision email sent to {} for pre-auth {}", request.getSenderEmail(), preAuth.getId());
                
            } catch (Exception e) {
                log.error("Failed to send decision email for pre-auth {}: {}", preAuth.getId(), e.getMessage());
            }
        });
    }

    private String buildHtmlBody(PreAuthorization preAuth, String memberName, String serviceName, String statusAr) {
        String status = preAuth.getStatus().name();
        String primaryColor = "APPROVED".equals(status) ? "#10b981" : ("REJECTED".equals(status) ? "#ef4444" : "#f59e0b");
        
        StringBuilder detailsHtml = new StringBuilder();
        if ("APPROVED".equals(status)) {
            detailsHtml.append("<tr><td><b>صالح حتى:</b></td><td>").append(preAuth.getExpiryDate()).append("</td></tr>");
        } else if ("REJECTED".equals(status)) {
            detailsHtml.append("<tr><td><b>سبب الرفض:</b></td><td>").append(preAuth.getRejectionReason()).append("</td></tr>");
        } else if ("NEEDS_CORRECTION".equals(status)) {
            detailsHtml.append("<tr><td><b>ملاحظات التصحيح:</b></td><td>").append(preAuth.getNotes()).append("</td></tr>");
        }

        return """
            <!DOCTYPE html>
            <html dir="rtl" lang="ar">
            <head>
                <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f7f6; margin: 0; padding: 0; color: #333; }
                    .container { max-width: 600px; margin: 20px auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                    .header { background-color: %s; color: white; padding: 30px; text-align: center; }
                    .header h1 { margin: 0; font-size: 24px; }
                    .content { padding: 30px; line-height: 1.6; text-align: right; }
                    .card { border: 1px solid #e2e8f0; border-radius: 6px; padding: 20px; margin-top: 20px; background-color: #f8fafc; }
                    .details-table { width: 100%%; border-collapse: collapse; margin-top: 15px; }
                    .details-table td { padding: 8px 0; border-bottom: 1px solid #edf2f7; }
                    .footer { background-color: #f8fafc; color: #718096; padding: 20px; text-align: center; font-size: 12px; }
                    .brand { color: #0d9488; font-weight: bold; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header" style="background-color: %s;">
                        <h1>قرار الموافقة الطبية</h1>
                        <div>المرجع: %s</div>
                    </div>
                    <div class="content">
                        <p>عزيزي الشريك الصحي،</p>
                        <p>نود إفادتكم بأنه تم الانتهاء من مراجعة طلب الموافقة الطبية الخاص بكم. وفيما يلي تفاصيل القرار:</p>
                        
                        <div class="card">
                            <h3 style="color: %s; margin-top: 0;">الحالة: %s</h3>
                            <table class="details-table">
                                <tr><td width="30%%"><b>اسم المشترك:</b></td><td>%s</td></tr>
                                <tr><td><b>الخدمة:</b></td><td>%s</td></tr>
                                <tr><td><b>تاريخ الطلب:</b></td><td>%s</td></tr>
                                %s
                            </table>
                        </div>
                        
                        <p style="margin-top: 20px;"><b>ملاحظات إضافية:</b><br>%s</p>
                    </div>
                    <div class="footer">
                        هذا البريد الإلكتروني آلي، يرجى عدم الرد مباشرة.<br>
                        <span class="brand">شركة وعد لإدارة النفقات الطبية (Waad Care)</span><br>
                        بنغازي، ليبيا.
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                primaryColor, 
                primaryColor, 
                preAuth.getReferenceNumber(),
                primaryColor,
                statusAr,
                memberName,
                serviceName,
                preAuth.getRequestDate(),
                detailsHtml.toString(),
                preAuth.getNotes() != null ? preAuth.getNotes() : "لا توجد ملاحظات إضافية."
            );
    }

    private String buildPdfHtml(PreAuthorization preAuth, com.waad.tba.modules.member.entity.Member member, String serviceName) {
        String memberName = member != null ? member.getFullName() : "---";
        String cardNumber = member != null ? member.getCardNumber() : "---";
        
        return """
            <!DOCTYPE html>
            <html dir="rtl" lang="ar">
            <head>
                <style>
                    @page { size: A4; margin: 20mm; }
                    body { font-family: 'Segoe UI', Tahoma, Verdana, sans-serif; text-align: right; }
                    .header { text-align: center; border-bottom: 2px solid #0d9488; padding-bottom: 10px; margin-bottom: 30px; }
                    .header h1 { color: #0d9488; margin: 0; }
                    .doc-info { margin-bottom: 30px; font-size: 14px; }
                    .details { width: 100%%; border-collapse: collapse; margin-top: 20px; }
                    .details td { padding: 10px; border: 1px solid #e2e8f0; }
                    .details th { background-color: #f8fafc; padding: 10px; border: 1px solid #e2e8f0; text-align: right; }
                    .footer { position: fixed; bottom: 0; width: 100%%; text-align: center; font-size: 10px; border-top: 1px solid #e2e8f0; padding-top: 10px; }
                    .stamp { border: 2px solid #0d9488; color: #0d9488; display: inline-block; padding: 10px; border-radius: 50%%; transform: rotate(-15deg); font-weight: bold; margin-top: 50px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>مستند موافقة طبية</h1>
                    <div style="font-size: 14px;">شركة وعد لإدارة النفقات الطبية (Waad Care)</div>
                </div>
                
                <div class="doc-info">
                    <b>رقم المرجع:</b> %s<br>
                    <b>تاريخ الإصدار:</b> %s<br>
                    <b>تاريخ الانتهاء:</b> %s
                </div>
                
                <h3>تفاصيل الموافقة:</h3>
                <table class="details">
                    <tr><th>اسم المشترك:</th><td>%s</td></tr>
                    <tr><th>رقم البطاقة:</th><td>%s</td></tr>
                    <tr><th>الخدمة الطبية:</th><td>%s</td></tr>
                </table>
                
                <div style="margin-top: 30px;">
                    <b>ملاحظات:</b>
                    <p>%s</p>
                </div>
                
                <div style="text-align: left;">
                    <div class="stamp">معتمد رقمياً<br>WAAD CARE</div>
                </div>
                
                <div class="footer">
                    Waad Care - Benghazi, Libya - support@alwahacare.com - +218 XX XXX XXXX
                </div>
            </body>
            </html>
            """.formatted(
                preAuth.getReferenceNumber(),
                preAuth.getApprovedAt() != null ? preAuth.getApprovedAt().toLocalDate() : "",
                preAuth.getExpiryDate() != null ? preAuth.getExpiryDate() : "",
                memberName,
                cardNumber,
                serviceName,
                preAuth.getNotes() != null ? preAuth.getNotes() : "لا توجد ملاحظات."
            );
    }

    private String translateStatus(String status) {
        return switch (status) {
            case "APPROVED" -> "موافق عليه";
            case "REJECTED" -> "مرفوض";
            case "NEEDS_CORRECTION" -> "يتطلب تصحيح";
            case "PENDING" -> "قيد المراجعة";
            default -> status;
        };
    }
}

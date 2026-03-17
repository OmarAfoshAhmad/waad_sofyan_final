package com.waad.tba.modules.pdf.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * PDF Company Settings Entity
 * 
 * Stores company branding and PDF generation configuration
 * for headers, footers, and page layout.
 * 
 * @since 2026-01-11
 */
@Entity
@Table(name = "pdf_company_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfCompanySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Company Branding ==========

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(name = "logo_data", columnDefinition = "bytea")
    private byte[] logoData;

    // ========== Contact Information ==========

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "website", length = 255)
    private String website;

    // ========== Footer Text ==========

    @Column(name = "footer_text", columnDefinition = "TEXT")
    private String footerText;

    @Column(name = "footer_text_en", columnDefinition = "TEXT")
    private String footerTextEn;

    // ========== Styling ==========

    @Column(name = "header_color", length = 7)
    private String headerColor;

    @Column(name = "footer_color", length = 7)
    private String footerColor;

    // ========== Page Settings ==========

    @Column(name = "page_size", length = 20)
    private String pageSize;

    @Column(name = "margin_top")
    private Integer marginTop;

    @Column(name = "margin_bottom")
    private Integer marginBottom;

    @Column(name = "margin_left")
    private Integer marginLeft;

    @Column(name = "margin_right")
    private Integer marginRight;

    // ========== Claim Report Configuration ==========

    @Column(name = "claim_report_title")
    private String claimReportTitle;

    @Column(name = "claim_report_primary_color", length = 7)
    private String claimReportPrimaryColor;

    @Column(name = "claim_report_intro", columnDefinition = "TEXT")
    private String claimReportIntro;

    @Column(name = "claim_report_footer_note", columnDefinition = "TEXT")
    private String claimReportFooterNote;

    @Column(name = "claim_report_sig_right_top")
    private String claimReportSigRightTop;

    @Column(name = "claim_report_sig_right_bottom")
    private String claimReportSigRightBottom;

    @Column(name = "claim_report_sig_left_top")
    private String claimReportSigLeftTop;

    @Column(name = "claim_report_sig_left_bottom")
    private String claimReportSigLeftBottom;

    // ========== Metadata ==========

    @Column(name = "is_active")
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Get logo as Base64 data URL for embedding in HTML/PDF
     */
    @Transient
    public String getLogoBase64DataUrl() {
        if (logoData == null || logoData.length == 0) {
            return null;
        }
        String base64 = java.util.Base64.getEncoder().encodeToString(logoData);
        return "data:image/png;base64," + base64;
    }

    /**
     * Check if logo is available
     */
    @Transient
    public boolean hasLogo() {
        return (logoData != null && logoData.length > 0) ||
                (logoUrl != null && !logoUrl.trim().isEmpty());
    }
}

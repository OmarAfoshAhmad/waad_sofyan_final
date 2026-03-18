package com.waad.tba.modules.provider.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Provider administrative documents (license, commercial register, tax
 * certificate, etc.)
 */
@Entity
@Table(name = "provider_admin_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ProviderAdminDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Document type labels (Arabic)
     */
    public static String getTypeLabel(String type) {
        return switch (type) {
            case "LICENSE" -> "رخصة مزاولة مهنة";
            case "COMMERCIAL_REGISTER" -> "سجل تجاري";
            case "TAX_CERTIFICATE" -> "شهادة ضريبية";
            case "CONTRACT_COPY" -> "نسخة العقد";
            case "OTHER" -> "أخرى";
            default -> type;
        };
    }
}

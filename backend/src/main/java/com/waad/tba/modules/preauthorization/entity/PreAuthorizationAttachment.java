package com.waad.tba.modules.preauthorization.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for PreAuthorization Attachments
 * Stores metadata about files attached to pre-authorizations
 */
@Entity
@Table(name = "pre_authorization_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the pre-authorization
     */
    @Column(name = "pre_authorization_id", nullable = false)
    private Long preAuthorizationId;

    /**
     * Optional reference to a specific line item
     */
    @Column(name = "line_id")
    private Long lineId;

    /**
     * Original file name as uploaded
     */
    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    /**
     * Stored file name (unique identifier)
     */
    @Column(name = "stored_file_name", length = 255)
    private String storedFileName;

    /**
     * File path in storage
     */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /**
     * MIME type of the file
     */
    @Column(name = "file_type", length = 100)
    private String fileType;

    /**
     * File size in bytes
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Type of attachment (INVOICE, MEDICAL_REPORT, PRESCRIPTION, LAB_RESULT, XRAY, OTHER)
     */
    @Column(name = "attachment_type", length = 50)
    @Builder.Default
    private String attachmentType = "OTHER";

    /**
     * Upload timestamp
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * User who uploaded the file
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}

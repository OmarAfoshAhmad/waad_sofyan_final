package com.waad.tba.modules.member.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed error record for a failed import row.
 */
@Entity
@Table(name = "member_import_errors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MemberImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_log_id", nullable = false)
    private MemberImportLog importLog;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "row_data", columnDefinition = "jsonb")
    private String rowData;  // JSON representation of the row

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", length = 50)
    private ErrorType errorType;

    @Column(name = "error_field", length = 100)
    private String errorField;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Error type classification
     */
    public enum ErrorType {
        VALIDATION,     // Field validation failed
        DUPLICATE,      // Duplicate record (civil ID exists)
        MAPPING,        // Column mapping error
        REFERENCE,      // Foreign key reference not found (employer, policy)
        SYSTEM          // System/database error
    }

    /**
     * Factory method for validation errors
     */
    public static MemberImportError validationError(MemberImportLog log, int rowNum, 
            String field, String message, String rowJson) {
        return MemberImportError.builder()
                .importLog(log)
                .rowNumber(rowNum)
                .errorType(ErrorType.VALIDATION)
                .errorField(field)
                .errorMessage(message)
                .rowData(rowJson)
                .build();
    }

    /**
     * Factory method for duplicate errors
     */
    public static MemberImportError duplicateError(MemberImportLog log, int rowNum,
            String nationalNumber, String rowJson) {
        return MemberImportError.builder()
                .importLog(log)
                .rowNumber(rowNum)
                .errorType(ErrorType.DUPLICATE)
                .errorField("national_id")
                .errorMessage("Duplicate national ID: " + nationalNumber)
                .rowData(rowJson)
                .build();
    }

    /**
     * Factory method for reference errors
     */
    public static MemberImportError referenceError(MemberImportLog log, int rowNum, 
            String field, String message, String rowJson) {
        return MemberImportError.builder()
                .importLog(log)
                .rowNumber(rowNum)
                .errorType(ErrorType.REFERENCE)
                .errorField(field)
                .errorMessage(message)
                .rowData(rowJson)
                .build();
    }

    /**
     * Factory method for system errors
     */
    public static MemberImportError systemError(MemberImportLog log, int rowNum, 
            String message, String rowJson) {
        return MemberImportError.builder()
                .importLog(log)
                .rowNumber(rowNum)
                .errorType(ErrorType.SYSTEM)
                .errorMessage(message)
                .rowData(rowJson)
                .build();
    }
}

package com.waad.tba.modules.errorlog.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_error_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ErrorLogSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private ErrorLogSeverity severity = ErrorLogSeverity.ERROR;

    @Column(name = "environment", length = 40)
    private String environment;

    @Column(name = "correlation_id", length = 80)
    private String correlationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 150)
    private String username;

    @Column(name = "role", length = 80)
    private String role;

    @Column(name = "http_method", length = 20)
    private String httpMethod;

    @Column(name = "path", length = 500)
    private String path;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "user_message", length = 1000)
    private String userMessage;

    @Column(name = "technical_message", length = 2000)
    private String technicalMessage;

    @Column(name = "exception_class", length = 255)
    private String exceptionClass;

    @Column(name = "stack_excerpt", length = 4000)
    private String stackExcerpt;

    @Column(name = "stack_hash", length = 80)
    private String stackHash;

    @Column(name = "frontend_route", length = 500)
    private String frontendRoute;

    @Column(name = "browser", length = 300)
    private String browser;

    @Column(name = "resolved", nullable = false)
    @Builder.Default
    private Boolean resolved = false;

    @Column(name = "resolved_by", length = 150)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "notes", length = 2000)
    private String notes;
}

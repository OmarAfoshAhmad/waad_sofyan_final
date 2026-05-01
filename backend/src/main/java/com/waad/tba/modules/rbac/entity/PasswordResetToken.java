package com.waad.tba.modules.rbac.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Password Reset Token Entity
 * 
 * Stores secure tokens for password reset workflow.
 * - One-time use only
 * - Expires after 1 hour
 * - Deleted/invalidated after use
 */
@Entity(name = "RbacPasswordResetToken")
@Table(name = "password_reset_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = true) // Changed to nullable to support email-only resets from Auth
    private Long userId;

    @Column(unique = true, nullable = true, length = 255) // Nullable to support Auth OTP flow
    private String token;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "otp", nullable = true)
    private String otp;

    @Column(name = "expires_at", nullable = true)
    private LocalDateTime expiresAt;

    @Column(name = "expiry_date", nullable = true)
    private LocalDateTime expiryDate;

    @PrePersist
    public void syncExpiry() {
        if (expiryDate == null) expiryDate = expiresAt;
        if (expiresAt == null) expiresAt = expiryDate;
    }

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Check if token is expired
     */
    public boolean isExpired() {
        if (expiresAt != null) {
            return LocalDateTime.now().isAfter(expiresAt);
        }
        // Fallback for OTP-based flow which uses expiryDate
        if (expiryDate != null) {
            return LocalDateTime.now().isAfter(expiryDate);
        }
        // No expiry set — treat as expired
        return true;
    }

    /**
     * Check if token is valid (not used and not expired)
     */
    public boolean isValid() {
        return !used && !isExpired();
    }

    /**
     * Mark token as used
     */
    public void markAsUsed() {
        this.used = true;
    }
}

package com.waad.tba.modules.preauthorization.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pre_auth_email_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthEmailRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", unique = true, length = 255)
    private String messageId;

    @Column(name = "sender_email", length = 255)
    private String senderEmail;

    @Column(name = "sender_name", length = 255)
    private String senderName;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "body_html", columnDefinition = "TEXT")
    private String bodyHtml;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "processed")
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "converted_to_pre_auth_id")
    private Long convertedToPreAuthId;

    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "detected_service_id")
    private Long detectedServiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.waad.tba.modules.provider.entity.Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.waad.tba.modules.member.entity.Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detected_service_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.waad.tba.modules.medicaltaxonomy.entity.MedicalService detectedService;

    @com.fasterxml.jackson.annotation.JsonProperty("providerName")
    public String getProviderName() {
        return provider != null ? provider.getName() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("memberFullName")
    public String getMemberFullName() {
        return member != null ? member.getFullName() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("serviceName")
    public String getServiceName() {
        return detectedService != null ? detectedService.getName() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("attachmentsCount")
    public int getAttachmentsCount() {
        return attachments != null ? attachments.size() : 0;
    }

    @OneToMany(mappedBy = "emailRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PreAuthEmailAttachment> attachments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

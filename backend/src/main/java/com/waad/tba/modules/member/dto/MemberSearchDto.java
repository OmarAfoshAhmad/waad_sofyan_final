package com.waad.tba.modules.member.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Unified DTO for Member Search Results (Phase 3: Barcode/QR Support)
 * Supports search by: Card Number, Name (fuzzy), or Barcode (QR)
 * 
 * Used for:
 * - Card Number Search (Phase 1) - instant indexed lookup
 * - Fuzzy Name Search (Phase 2) - Arabic intelligent search
 * - Barcode/QR Search (Phase 3) - QR code scanning
 * 
 * @author TBA System
 * @version 3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Unified Member Search Result DTO - supports Card/Name/Barcode search")
public class MemberSearchDto {

    @Schema(description = "Member ID", example = "12345")
    private Long id;

    @Schema(description = "Full name (Arabic or English)", example = "أحمد محمد علي")
    private String fullName;

    @Schema(description = "Card number", example = "1234567890")
    private String cardNumber;

    @Schema(description = "Barcode/QR UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String barcode;

    @Schema(description = "Member status", example = "ACTIVE", allowableValues = {"ACTIVE", "EXPIRED", "SUSPENDED", "BLOCKED"})
    private String status;

    @Schema(description = "Card status", example = "ACTIVE", allowableValues = {"ACTIVE", "BLOCKED", "EXPIRED"})
    private String cardStatus;

    @Schema(description = "Eligibility status", example = "true")
    private Boolean eligible;

    @Schema(description = "Employer organization name", example = "شركة الوعد للتأمين")
    private String employerName;

    @Schema(description = "Benefit policy name", example = "سياسة الموظفين الأساسية")
    private String policyName;

    @Schema(description = "Co-payment amount (percentage)", example = "10")
    private Integer copayAmount;


    @Schema(description = "Membership start date", example = "2024-01-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @Schema(description = "Membership end date", example = "2024-12-31")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Schema(description = "National number (optional)", example = "28912345678901")
    private String nationalNumber;

    @Schema(description = "Phone number", example = "+966501234567")
    private String phone;

    @Schema(description = "Email address", example = "ahmed@example.com")
    private String email;

    @Schema(description = "Status message (eligibility/error info)", example = "العضوية نشطة - يمكن بدء زيارة")
    private String message;

    @Schema(description = "Search match type", example = "BARCODE", allowableValues = {"CARD_NUMBER", "NAME_FUZZY", "BARCODE"})
    private String searchType;

    @Schema(description = "Search similarity score (for fuzzy name search)", example = "0.85")
    private Double similarityScore;

    /**
     * Factory method for building from Member entity with eligibility info
     */
    public static MemberSearchDto fromMember(
            com.waad.tba.modules.member.entity.Member member,
            String searchType,
            Double similarityScore
    ) {
        MemberSearchDtoBuilder builder = MemberSearchDto.builder()
                .id(member.getId())
                .fullName(member.getFullName())
                .cardNumber(member.getCardNumber())
                .barcode(member.getBarcode())
                .status(member.getStatus().name())
                .cardStatus(member.getCardStatus().name())
                .eligible(member.getEligibilityStatus())
                .startDate(member.getStartDate())
                .endDate(member.getEndDate())
                .nationalNumber(member.getNationalNumber())
                .phone(member.getPhone())
                .email(member.getEmail())
                .searchType(searchType);

        // Add employer info
        if (member.getEmployer() != null) {
            builder.employerName(member.getEmployer().getName());
        }

        // Add benefit policy info
        if (member.getBenefitPolicy() != null) {
            builder.policyName(member.getBenefitPolicy().getName())
                   .copayAmount(member.getBenefitPolicy().getDefaultCoveragePercent() != null ?
                       100 - member.getBenefitPolicy().getDefaultCoveragePercent() : null);
            // coverageLimit used to be set here from the policy's annual limit.
            // It was named as though it were this member's coverage and was the
            // policy's, which stopped being the same number the day an
            // exceptional uplift could raise one member's ceiling. No screen
            // read it, so it is gone rather than made to do a per-row ceiling
            // read: the search endpoint returns identity, not balances, and a
            // balance belongs to the reader that knows how to compute one.
        }

        // Add similarity score for fuzzy search
        if (similarityScore != null) {
            builder.similarityScore(similarityScore);
        }

        // Build message
        builder.message(buildMessage(member));

        return builder.build();
    }

    /**
     * Build status message based on member state
     */
    private static String buildMessage(com.waad.tba.modules.member.entity.Member member) {
        if (!member.getEligibilityStatus()) {
            return "العضوية غير مؤهلة - يرجى التحقق من الحالة";
        }

        if (member.getStatus() == com.waad.tba.modules.member.entity.Member.MemberStatus.TERMINATED) {
            return "العضوية منتهية - يرجى التجديد";
        }

        if (member.getStatus() == com.waad.tba.modules.member.entity.Member.MemberStatus.SUSPENDED) {
            return "العضوية موقوفة مؤقتاً";
        }

        if (member.getCardStatus() == com.waad.tba.modules.member.entity.Member.CardStatus.BLOCKED) {
            String reason = member.getBlockedReason();
            return reason != null ? "البطاقة محظورة: " + reason : "البطاقة محظورة";
        }

        if (member.getCardStatus() == com.waad.tba.modules.member.entity.Member.CardStatus.EXPIRED) {
            return "البطاقة منتهية - يرجى إصدار بطاقة جديدة";
        }

        return "العضوية نشطة - يمكن بدء زيارة";
    }
}

package com.waad.tba.modules.backlog.dto;

import com.waad.tba.common.enums.NetworkType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacklogClaimRequest {
    @NotNull(message = "يجب تحديد المؤمن عليه")
    private Long memberId;

    @NotNull(message = "يجب تحديد المزود")
    private Long providerId;

    @NotNull(message = "تاريخ الخدمة مطلوب")
    @PastOrPresent(message = "تاريخ الخدمة لا يمكن أن يكون في المستقبل")
    private LocalDate serviceDate;

    private String doctorName;
    private String diagnosis;
    private String complaint;
    private String legacyReferenceNumber;
    private String notes;
    private NetworkType networkStatus;
    private String status; // Optional: SETTLED, REJECTED
    private String rejectionReason;

    @NotEmpty(message = "يجب إدخال بند واحد على الأقل")
    @Valid
    private List<BacklogServiceLineDto> lines;
}

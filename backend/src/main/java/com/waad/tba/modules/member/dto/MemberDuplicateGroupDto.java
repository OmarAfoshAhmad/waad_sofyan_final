package com.waad.tba.modules.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberDuplicateGroupDto {
    private String normalizedName;
    private Long employerId;
    private Long parentId;
    private String employerName;
    private String parentCardNumber;
    private String parentName;
    private boolean isPrincipal;
    private List<DuplicateMemberInfo> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicateMemberInfo {
        private Long id;
        private String fullName;
        private String cardNumber;
        private String nationalNumber;
        private LocalDate birthDate;
        private LocalDateTime createdAt;
        private String relationship;
        private String gender;
        // counts for info purposes
        private int visitCount;
        private int claimCount;
        private int dependentCount;
        private List<String> dependentNames;
    }
}

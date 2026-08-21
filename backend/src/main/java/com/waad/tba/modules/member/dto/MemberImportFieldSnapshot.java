package com.waad.tba.modules.member.dto;

import java.time.LocalDate;

import com.waad.tba.modules.member.entity.Member;

import lombok.Builder;
import lombok.Value;

/**
 * Exactly the fields {@link com.waad.tba.modules.member.service.MemberImportRowProcessor
 * #processRowForImport} can mutate on an existing member -- nothing more.
 * Captured before an import row is applied, and restored verbatim by a
 * rollback. Deliberately NOT a serialization of the whole entity: that would
 * either drag in lazy associations or silently miss the day a new import-writable
 * field is added, whereas this list is the one place both the writer and the
 * undo must agree on.
 */
@Value
@Builder
public class MemberImportFieldSnapshot {
    String fullName;
    Long employerId;
    Long benefitPolicyId;
    Long parentId;
    String relationship;
    String cardStatus;
    String cardNumber;
    String barcode;
    String nationalNumber;
    LocalDate birthDate;
    String gender;
    String phone;
    String email;
    String employeeNumber;
    String policyNumber;
    LocalDate startDate;

    public static MemberImportFieldSnapshot of(Member m) {
        return MemberImportFieldSnapshot.builder()
                .fullName(m.getFullName())
                .employerId(m.getEmployer() == null ? null : m.getEmployer().getId())
                .benefitPolicyId(m.getBenefitPolicy() == null ? null : m.getBenefitPolicy().getId())
                .parentId(m.getParent() == null ? null : m.getParent().getId())
                .relationship(m.getRelationship() == null ? null : m.getRelationship().name())
                .cardStatus(m.getCardStatus() == null ? null : m.getCardStatus().name())
                .cardNumber(m.getCardNumber())
                .barcode(m.getBarcode())
                .nationalNumber(m.getNationalNumber())
                .birthDate(m.getBirthDate())
                .gender(m.getGender() == null ? null : m.getGender().name())
                .phone(m.getPhone())
                .email(m.getEmail())
                .employeeNumber(m.getEmployeeNumber())
                .policyNumber(m.getPolicyNumber())
                .startDate(m.getStartDate())
                .build();
    }
}

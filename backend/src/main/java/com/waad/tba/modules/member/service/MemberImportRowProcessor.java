package com.waad.tba.modules.member.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.MemberImportPreviewDto.ImportValidationErrorDto;
import com.waad.tba.modules.member.dto.MemberImportPreviewDto.MemberImportRowDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.Member.Gender;
import com.waad.tba.modules.member.entity.Member.MemberStatus;
import com.waad.tba.modules.member.entity.Member.Relationship;
import com.waad.tba.modules.member.entity.MemberAttribute;
import com.waad.tba.modules.member.entity.MemberAttribute.AttributeSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles the logic for processing individual rows during preview and import.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberImportRowProcessor {

    private final MemberImportParser parser;
    private final EmployerRepository employerRepository;
    private final BenefitPolicyRepository benefitPolicyRepository;
    private final BarcodeGeneratorService barcodeGeneratorService;
    private final CardNumberGeneratorService cardNumberGeneratorService;
    private final Map<String, Optional<Employer>> employerCache = new java.util.concurrent.ConcurrentHashMap<>();

    private Optional<Employer> findEmployerCached(String nameOrCode) {
        if (nameOrCode == null || nameOrCode.isBlank()) return Optional.empty();
        String lookupValue = nameOrCode.trim();
        String cacheKey = lookupValue.toLowerCase();
        return employerCache.computeIfAbsent(cacheKey, key -> {
            return employerRepository.findByNameIgnoreCase(lookupValue)
                    .or(() -> employerRepository.findByCode(lookupValue));
        });
    }


    public MemberImportRowDto parseRowForPreview(Row row, int rowNum,
            Map<String, Integer> fieldToColumnIndex,
            List<ImportValidationErrorDto> validationErrors,
            Set<String> seenCardNumbers,
            Employer defaultEmployer) {

        List<String> rowErrors = new ArrayList<>();
        List<String> rowWarnings = new ArrayList<>();
        Map<String, String> attributes = new HashMap<>();
        String status = "NEW";
        boolean hasError = false;
        boolean hasWarning = false;

        String cardNumber = parser.getFieldValue(row, fieldToColumnIndex, "cardNumber");
        String fullName = parser.getFieldValue(row, fieldToColumnIndex, "fullName");
        String employerName = parser.getFieldValue(row, fieldToColumnIndex, "employer");
        String civilId = parser.getFieldValue(row, fieldToColumnIndex, "nationalNumber");
        String membershipStatus = parser.getFieldValue(row, fieldToColumnIndex, "memberStatus");

        if (fullName == null || fullName.isBlank()) {
            rowErrors.add("الاسم الكامل مطلوب (Full name is required)");
            validationErrors.add(ImportValidationErrorDto.builder()
                    .rowNumber(rowNum).field("full_name").severity("ERROR")
                    .message("الاسم الكامل مطلوب - Full name is required").build());
            hasError = true;
        }

        if (civilId == null || civilId.isBlank()) {
            rowWarnings.add("الرقم الوطني غير موجود - الحقل اختياري لكن يُفضّل إضافته");
            hasWarning = true;
        }

        if (employerName == null || employerName.isBlank()) {
            if (defaultEmployer == null) {
                rowErrors.add("جهة العمل مطلوبة (Employer is required)");
                validationErrors.add(ImportValidationErrorDto.builder()
                        .rowNumber(rowNum).field("employer").severity("ERROR")
                        .message("جهة العمل مطلوبة - Employer is required").build());
                hasError = true;
            } else {
                rowWarnings.add("جهة العمل غير موجودة في الصف - سيتم استخدام جهة العمل المختارة");
                hasWarning = true;
            }
        } else {
            Optional<Employer> employerOpt = findEmployerCached(employerName);

            if (employerOpt.isEmpty()) {
                if (defaultEmployer == null) {
                    rowErrors.add("جهة العمل غير موجودة: " + employerName);
                    validationErrors.add(ImportValidationErrorDto.builder()
                            .rowNumber(rowNum).field("employer").value(employerName).severity("ERROR")
                            .message("جهة العمل غير موجودة - Employer not found: " + employerName).build());
                    hasError = true;
                } else {
                    rowWarnings.add("جهة العمل غير معروفة: " + employerName + " - سيتم استخدام جهة العمل المختارة");
                    hasWarning = true;
                }
            }
        }

        if (cardNumber != null && !cardNumber.isBlank()) {
            if (seenCardNumbers.contains(cardNumber)) {
                rowWarnings.add("رقم بطاقة مكرر في الملف: " + cardNumber + " (سيتم تجاهله وإنشاء رقم جديد)");
                hasWarning = true;
            } else {
                seenCardNumbers.add(cardNumber);
            }
        }

        if (membershipStatus != null && !membershipStatus.isBlank()) {
            try {
                parser.parseMemberStatus(membershipStatus);
                String normalized = parser.normalizeExcelValue(membershipStatus).toLowerCase();
                if (normalized.equals("مكتمل") || normalized.equals("completed")) {
                    rowWarnings.add("مكتمل هي حالة استهلاك سقف وليست حالة عضوية؛ سيبقى المستفيد نشطًا ويجب استيراد الرصيد في ورقة الأرصدة الافتتاحية");
                    hasWarning = true;
                }
            } catch (IllegalArgumentException ex) {
                rowErrors.add(ex.getMessage());
                validationErrors.add(ImportValidationErrorDto.builder()
                        .rowNumber(rowNum).field("member_status").value(membershipStatus).severity("ERROR")
                        .message(ex.getMessage()).build());
                hasError = true;
            }
        }

        // Attributes
        for (Map.Entry<String, Integer> entry : fieldToColumnIndex.entrySet()) {
            if (entry.getKey().startsWith("attr:")) {
                String attrCode = entry.getKey().substring(5);
                String attrValue = parser.getCellStringValue(row.getCell(entry.getValue()));
                if (attrValue != null && !attrValue.isBlank()) {
                    attributes.put(attrCode, attrValue);
                }
            }
        }

        if (hasError)
            status = "ERROR";
        else if (hasWarning)
            status = "WARNING";

        return MemberImportRowDto.builder()
                .rowNumber(rowNum).cardNumber(cardNumber).fullName(fullName)
                .employerName(employerName).attributes(attributes).status(status)
                .errors(rowErrors).warnings(rowWarnings).build();
    }

    public Member processRowForImport(Row row, int rowNum,
            Map<String, Integer> fieldToColumnIndex,
            Employer defaultEmployer,
            BenefitPolicy benefitPolicy,
            Member parent,
            Relationship relationship,
            Member existingMember) {

        String fullName = parser.getFieldValue(row, fieldToColumnIndex, "fullName");
        String civilId = parser.getFieldValue(row, fieldToColumnIndex, "nationalNumber");
        String policyNumber = parser.getFieldValue(row, fieldToColumnIndex, "policyNumber");
        String startDateStr = parser.getFieldValue(row, fieldToColumnIndex, "startDate");
        String memberStatusStr = parser.getFieldValue(row, fieldToColumnIndex, "memberStatus");
        MemberStatus importedStatus = parser.parseMemberStatus(memberStatusStr);

        if (fullName == null || fullName.isBlank()) {
            throw new BusinessRuleException("الصف " + rowNum + ": الاسم الكامل مطلوب");
        }

        Employer rowEmployer = resolveEmployerForRow(row, rowNum, fieldToColumnIndex, defaultEmployer);

        // A dependent always belongs to the principal's employer. Prefer an existing
        // principal policy; otherwise resolve the selected/effective employer policy.
        Employer finalEmployer = parent != null ? parent.getEmployer() : rowEmployer;
        BenefitPolicy policyCandidate = parent != null && parent.getBenefitPolicy() != null
                ? parent.getBenefitPolicy()
                : benefitPolicy;
        BenefitPolicy resolvedPolicy = resolveAndValidatePolicy(policyCandidate, finalEmployer, rowNum);
        BenefitPolicy finalPolicy = resolvedPolicy;
        String finalPolicyNumber = parent != null && parent.getPolicyNumber() != null
                && !parent.getPolicyNumber().isBlank()
                        ? parent.getPolicyNumber()
                        : policyNumber;

        if (parent != null && parent.getBenefitPolicy() == null) {
            // Heal an old unlinked principal while importing one of their dependents.
            parent.setBenefitPolicy(resolvedPolicy);
            if (parent.getPolicyNumber() == null || parent.getPolicyNumber().isBlank()) {
                parent.setPolicyNumber(resolvedPolicy.getPolicyCode());
            }
            finalPolicyNumber = parent.getPolicyNumber();
        }

        Member member;
        if (existingMember != null) {
            member = existingMember;
            member.setFullName(fullName);
            member.setEmployer(finalEmployer);
            member.setBenefitPolicy(finalPolicy);
            member.setParent(parent);
            member.setRelationship(relationship);
            member.setStatus(importedStatus);
            member.setCardStatus(importedStatus == MemberStatus.TERMINATED
                    ? Member.CardStatus.INACTIVE : Member.CardStatus.ACTIVE);
            // Suspended/Pending remain visible in operational lists; eligibility
            // is denied by MemberStatus, while TERMINATED is the archival state.
            member.setActive(importedStatus != MemberStatus.TERMINATED);
            member.getAttributes().clear();
        } else {
            member = Member.builder()
                    .fullName(fullName)
                    .employer(finalEmployer)
                    .benefitPolicy(finalPolicy)
                    .status(importedStatus)
                    .cardStatus(importedStatus == MemberStatus.TERMINATED
                            ? Member.CardStatus.INACTIVE : Member.CardStatus.ACTIVE)
                    .active(importedStatus != MemberStatus.TERMINATED)
                    .parent(parent)
                    .relationship(relationship)
                    .build();
        }

        // Set card number: use value from Excel if present, otherwise generate a unique one
        String cardNumber = parser.getFieldValue(row, fieldToColumnIndex, "cardNumber");
        if (cardNumber != null && !cardNumber.isBlank()) {
            member.setCardNumber(cardNumber.trim());
        } else if (member.getCardNumber() == null) {
            if (parent == null) {
                member.setCardNumber(cardNumberGeneratorService.generateUniqueForPrincipal(member));
            } else {
                member.setCardNumber(cardNumberGeneratorService.generateForDependent(parent, relationship));
            }
        }
        member.setBarcode(member.getCardNumber());

        if (civilId != null && !civilId.isBlank())
            member.setNationalNumber(civilId);

        // Optional fields
        String birthDateStr = parser.getFieldValue(row, fieldToColumnIndex, "birthDate");
        if (birthDateStr != null && !birthDateStr.isBlank()) {
            LocalDate birthDate = parser.parseDate(birthDateStr);
            if (birthDate != null)
                member.setBirthDate(birthDate);
        }

        String genderStr = parser.getFieldValue(row, fieldToColumnIndex, "gender");
        if (genderStr != null && !genderStr.isBlank()) {
            Gender gender = parser.parseGender(genderStr);
            if (gender != null)
                member.setGender(gender);
        }

        String phone = parser.getFieldValue(row, fieldToColumnIndex, "phone");
        if (phone != null && !phone.isBlank())
            member.setPhone(phone);

        String email = parser.getFieldValue(row, fieldToColumnIndex, "email");
        if (email != null && !email.isBlank())
            member.setEmail(email);

        String employeeNumber = parser.getFieldValue(row, fieldToColumnIndex, "employeeNumber");
        if (employeeNumber != null && !employeeNumber.isBlank())
            member.setEmployeeNumber(employeeNumber);

        if (finalPolicyNumber != null && !finalPolicyNumber.isBlank()) {
            member.setPolicyNumber(finalPolicyNumber);
        } else {
            member.setPolicyNumber(finalPolicy.getPolicyCode());
        }

        if (startDateStr != null && !startDateStr.isBlank()) {
            LocalDate parsedStartDate = parser.parseDate(startDateStr);
            if (parsedStartDate != null)
                member.setStartDate(parsedStartDate);
        }

        // Attributes
        String jobTitle = parser.getFieldValue(row, fieldToColumnIndex, "jobTitle");
        if (jobTitle != null && !jobTitle.isBlank()) {
            member.getAttributes().add(MemberAttribute.builder()
                    .member(member).attributeCode("job_title").attributeValue(jobTitle)
                    .source(AttributeSource.IMPORT).build());
        }

        String department = parser.getFieldValue(row, fieldToColumnIndex, "department");
        if (department != null && !department.isBlank()) {
            member.getAttributes().add(MemberAttribute.builder()
                    .member(member).attributeCode("department").attributeValue(department)
                    .source(AttributeSource.IMPORT).build());
        }

        return member;
    }

    BenefitPolicy resolveAndValidatePolicy(BenefitPolicy selectedPolicy, Employer employer, int rowNum) {
        if (employer == null) {
            throw new BusinessRuleException("الصف " + rowNum + ": تعذر تحديد جهة العمل لربط وثيقة المنافع");
        }

        BenefitPolicy resolved = selectedPolicy != null
                ? selectedPolicy
                : benefitPolicyRepository
                        .findActiveEffectivePolicyForEmployer(employer.getId(), LocalDate.now())
                        .orElseThrow(() -> new BusinessRuleException(
                                "الصف " + rowNum + ": لا توجد وثيقة منافع فعالة لجهة العمل " + employer.getName()));

        if (resolved.getEmployer() == null || !employer.getId().equals(resolved.getEmployer().getId())) {
            throw new BusinessRuleException(
                    "الصف " + rowNum + ": وثيقة المنافع المختارة لا تتبع جهة عمل المستفيد");
        }
        if (!resolved.isEffectiveOn(LocalDate.now())) {
            throw new BusinessRuleException(
                    "الصف " + rowNum + ": وثيقة المنافع المختارة غير فعالة في تاريخ الاستيراد");
        }
        return resolved;
    }

    Employer resolveEmployerForRow(Row row, int rowNum, Map<String, Integer> fieldToColumnIndex,
            Employer defaultEmployer) {
        String employerNameOrCode = parser.getFieldValue(row, fieldToColumnIndex, "employer");
        if (employerNameOrCode == null || employerNameOrCode.isBlank()) {
            if (defaultEmployer != null)
                return defaultEmployer;
            throw new BusinessRuleException("الصف " + rowNum + ": جهة العمل مطلوبة");
        }

        String normalized = employerNameOrCode.trim();
        Optional<Employer> resolvedOptional = findEmployerCached(normalized);

        if (resolvedOptional.isEmpty()) {
            if (defaultEmployer != null)
                return defaultEmployer;
            throw new BusinessRuleException("الصف " + rowNum + ": جهة العمل غير موجودة: " + normalized);
        }

        return resolvedOptional.get();
    }
}

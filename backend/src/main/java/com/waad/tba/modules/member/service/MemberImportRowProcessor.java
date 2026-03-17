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
            Optional<Employer> employerOpt = employerRepository.findByNameIgnoreCase(employerName)
                    .or(() -> employerRepository.findByCode(employerName));

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
            BenefitPolicy benefitPolicy) {

        String fullName = parser.getFieldValue(row, fieldToColumnIndex, "fullName");
        String civilId = parser.getFieldValue(row, fieldToColumnIndex, "nationalNumber");
        String policyNumber = parser.getFieldValue(row, fieldToColumnIndex, "policyNumber");
        String startDateStr = parser.getFieldValue(row, fieldToColumnIndex, "startDate");

        if (fullName == null || fullName.isBlank()) {
            throw new BusinessRuleException("الصف " + rowNum + ": الاسم الكامل مطلوب");
        }

        Employer rowEmployer = resolveEmployerForRow(row, rowNum, fieldToColumnIndex, defaultEmployer);

        BenefitPolicy resolvedPolicy = benefitPolicy;
        if (resolvedPolicy == null && rowEmployer != null) {
            resolvedPolicy = benefitPolicyRepository
                    .findActiveEffectivePolicyForEmployer(rowEmployer.getId(), LocalDate.now())
                    .orElse(null);
        }

        Member member = Member.builder()
                .fullName(fullName)
                .employer(rowEmployer)
                .benefitPolicy(resolvedPolicy)
                .status(MemberStatus.ACTIVE)
                .cardStatus(Member.CardStatus.ACTIVE)
                .active(true)
                .barcode(barcodeGeneratorService.generateForPrincipal())
                .build();

        // Set card number: use value from Excel if present, otherwise generate a unique
        // one
        String cardNumber = parser.getFieldValue(row, fieldToColumnIndex, "cardNumber");
        if (cardNumber != null && !cardNumber.isBlank()) {
            member.setCardNumber(cardNumber.trim());
        } else {
            member.setCardNumber(cardNumberGeneratorService.generateUniqueForPrincipal(member));
        }

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

        if (policyNumber != null && !policyNumber.isBlank())
            member.setPolicyNumber(policyNumber);

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

    private Employer resolveEmployerForRow(Row row, int rowNum, Map<String, Integer> fieldToColumnIndex,
            Employer defaultEmployer) {
        String employerNameOrCode = parser.getFieldValue(row, fieldToColumnIndex, "employer");
        if (employerNameOrCode == null || employerNameOrCode.isBlank()) {
            if (defaultEmployer != null)
                return defaultEmployer;
            throw new BusinessRuleException("الصف " + rowNum + ": جهة العمل مطلوبة");
        }

        String normalized = employerNameOrCode.trim();
        Optional<Employer> resolvedOptional = employerRepository.findByNameIgnoreCase(normalized)
                .or(() -> employerRepository.findByCode(normalized));

        if (resolvedOptional.isEmpty()) {
            if (defaultEmployer != null)
                return defaultEmployer;
            throw new BusinessRuleException("الصف " + rowNum + ": جهة العمل غير موجودة: " + normalized);
        }

        return resolvedOptional.get();
    }
}

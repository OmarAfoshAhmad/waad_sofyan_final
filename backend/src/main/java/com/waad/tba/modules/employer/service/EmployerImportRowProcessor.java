package com.waad.tba.modules.employer.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyCreateDto;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyResponseDto;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyService;
import com.waad.tba.modules.employer.dto.EmployerCreateDto;
import com.waad.tba.modules.employer.dto.EmployerImportRowDto;
import com.waad.tba.modules.employer.dto.EmployerResponseDto;
import com.waad.tba.modules.employer.dto.EmployerUpdateDto;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Creates or updates one imported row's employer AND makes sure it ends up
 * with an insurance policy (وثيقة تأمين) if it doesn't already have an active one.
 *
 * Lives in its own bean (not a method on {@link EmployerImportService}) so its
 * {@code @Transactional} boundary is actually honored per row — Spring's proxy
 * does not intercept self-invocation, so a same-class loop calling this as a
 * plain method would silently run without transactional rollback.
 *
 * Import never overwrites data blindly: a blank cell in the sheet keeps the
 * employer's current value for that field (see {@link #merge}), and an
 * employer that already has ANY policy (draft or active) is left with it
 * untouched — a new policy is only created when none exists yet, so
 * re-importing the same employer never piles up duplicate draft policies.
 *
 * The created policy is intentionally left in DRAFT status: import sets only
 * its policy-wide default coverage percentage (from the row, or 100% if
 * blank) and creates no per-category coverage rules, so it must be reviewed
 * and activated manually once someone adds real coverage rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployerImportRowProcessor {

    private final EmployerService employerService;
    private final EmployerRepository employerRepository;
    private final BenefitPolicyService benefitPolicyService;
    private final BenefitPolicyRepository benefitPolicyRepository;

    public record Created(EmployerResponseDto employer, BenefitPolicyResponseDto policy,
                          EmployerImportRowDto.Action action, boolean policyAlreadyExisted) {
    }

    /**
     * @param row                a structurally valid row (caller must check {@link EmployerImportRowDto#isValid()})
     * @param defaultAnnualLimit fallback annual limit when the row didn't specify one
     */
    @Transactional
    public Created ensureEmployerAndPolicy(EmployerImportRowDto row, BigDecimal defaultAnnualLimit) {
        EmployerResponseDto employer = row.getExistingEmployerId() == null
                ? createEmployer(row)
                : updateEmployerIfChanged(row);

        boolean hasExistingPolicy = !benefitPolicyRepository
                .findByEmployerIdAndActiveTrue(employer.getId())
                .isEmpty();

        if (hasExistingPolicy) {
            return new Created(employer, null, row.getAction(), true);
        }

        BenefitPolicyResponseDto policy = createDraftPolicy(employer, row, defaultAnnualLimit);
        return new Created(employer, policy, row.getAction(), false);
    }

    private EmployerResponseDto createEmployer(EmployerImportRowDto row) {
        String code = (row.getCode() == null || row.getCode().isBlank())
                ? employerService.generateNextCode()
                : row.getCode();

        EmployerCreateDto dto = new EmployerCreateDto();
        dto.setCode(code);
        dto.setName(row.getName());
        dto.setPhone(row.getPhone());
        dto.setEmail(row.getEmail());
        dto.setAddress(row.getAddress());

        return employerService.create(dto);
    }

    /** Only calls update() when at least one field actually differs — see {@link EmployerImportRowDto#getAction()}. */
    private EmployerResponseDto updateEmployerIfChanged(EmployerImportRowDto row) {
        Employer existing = employerRepository.findById(row.getExistingEmployerId())
                .orElseThrow(() -> new BusinessRuleException("جهة العمل لم تعد موجودة"));

        if (row.getAction() != EmployerImportRowDto.Action.UPDATE) {
            return employerService.getById(existing.getId());
        }

        EmployerUpdateDto dto = new EmployerUpdateDto();
        dto.setCode(existing.getCode());
        dto.setName(merge(row.getName(), existing.getName()));
        dto.setPhone(merge(row.getPhone(), existing.getPhone()));
        dto.setEmail(merge(row.getEmail(), existing.getEmail()));
        dto.setAddress(merge(row.getAddress(), existing.getAddress()));
        // Fields this import doesn't touch must be carried over as-is —
        // EmployerUpdateDto is a full overwrite, not a partial patch.
        dto.setBusinessType(existing.getBusinessType());
        dto.setWebsite(existing.getWebsite());
        dto.setLogoUrl(existing.getLogoUrl());
        dto.setCrNumber(existing.getCrNumber());
        dto.setTaxNumber(existing.getTaxNumber());
        dto.setContractStartDate(existing.getContractStartDate());
        dto.setContractEndDate(existing.getContractEndDate());
        dto.setMaxMemberLimit(existing.getMaxMemberLimit());

        return employerService.update(existing.getId(), dto);
    }

    /**
     * Creates the policy with only its policy-wide default coverage percentage set
     * (from the row, or 100% if left blank) and no coverage rules — it stays DRAFT
     * until someone adds real coverage rules and activates it manually.
     */
    private BenefitPolicyResponseDto createDraftPolicy(EmployerResponseDto employer,
            EmployerImportRowDto row, BigDecimal defaultAnnualLimit) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusYears(1);
        BigDecimal annualLimit = row.getAnnualLimit() != null ? row.getAnnualLimit() : defaultAnnualLimit;
        Integer coveragePercent = row.getCoveragePercent() != null ? row.getCoveragePercent() : 100;

        BenefitPolicyCreateDto policyDto = BenefitPolicyCreateDto.builder()
                .name("وثيقة (مسودة) - " + employer.getName())
                .employerOrgId(employer.getId())
                .startDate(startDate)
                .endDate(endDate)
                .annualLimit(annualLimit)
                .defaultCoveragePercent(coveragePercent)
                .build();

        return benefitPolicyService.create(policyDto);
    }

    /** A blank/absent row value keeps the employer's current value; a non-blank one replaces it. */
    private static String merge(String rowValue, String currentValue) {
        return (rowValue == null || rowValue.isBlank()) ? currentValue : rowValue;
    }
}

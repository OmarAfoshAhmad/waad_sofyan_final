package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.Member.Relationship;

class MemberImportRowProcessorPolicyTest {

    private BenefitPolicyRepository policyRepository;
    private MemberImportRowProcessor processor;
    private Employer employer;
    private BenefitPolicy policy;

    @BeforeEach
    void setUp() {
        policyRepository = mock(BenefitPolicyRepository.class);
        BarcodeGeneratorService barcodeGenerator = mock(BarcodeGeneratorService.class);
        CardNumberGeneratorService cardGenerator = mock(CardNumberGeneratorService.class);
        when(barcodeGenerator.generateForPrincipal()).thenReturn("BAR-1");
        when(cardGenerator.generateUniqueForPrincipal(any(Member.class))).thenReturn("CARD-1");

        MemberStatusTransitionService statusTransitionService = new MemberStatusTransitionService(
                mock(com.waad.tba.modules.member.repository.MemberRepository.class),
                mock(com.waad.tba.modules.member.repository.MemberStatusHistoryRepository.class),
                mock(com.waad.tba.modules.member.repository.MemberHardDeleteAuditRepository.class),
                policyRepository,
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
        processor = new MemberImportRowProcessor(
                new MemberImportParser(), mock(EmployerRepository.class), policyRepository,
                barcodeGenerator, cardGenerator, statusTransitionService);
        employer = Employer.builder().id(10L).code("EMP").name("Employer").active(true).build();
        policy = BenefitPolicy.builder()
                .id(20L).name("Policy").policyCode("POL-20").employer(employer)
                .startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(1))
                .status(BenefitPolicyStatus.ACTIVE).build();
    }

    @Test
    void importsPrincipalWithAutomaticallyResolvedPolicyAndCanonicalPolicyNumber() throws Exception {
        when(policyRepository.findActiveEffectivePolicyForEmployer(10L, LocalDate.now()))
                .thenReturn(Optional.of(policy));

        Member member = process(null, null, null);

        assertThat(member.getBenefitPolicy()).isSameAs(policy);
        assertThat(member.getPolicyNumber()).isEqualTo("POL-20");
    }

    @Test
    void rejectsActiveMemberWhenEmployerHasNoEffectivePolicy() {
        when(policyRepository.findActiveEffectivePolicyForEmployer(10L, LocalDate.now()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> process(null, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("لا توجد وثيقة منافع فعالة");
    }

    @Test
    void rejectsSelectedPolicyBelongingToAnotherEmployer() {
        Employer other = Employer.builder().id(99L).name("Other").code("OTHER").active(true).build();
        BenefitPolicy wrongPolicy = BenefitPolicy.builder()
                .id(30L).name("Wrong").policyCode("WRONG").employer(other)
                .startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(1))
                .status(BenefitPolicyStatus.ACTIVE).build();

        assertThatThrownBy(() -> process(wrongPolicy, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("لا تتبع جهة عمل المستفيد");
    }

    @Test
    void healsLegacyUnlinkedParentAndUsesResolvedPolicyForDependent() throws Exception {
        when(policyRepository.findActiveEffectivePolicyForEmployer(10L, LocalDate.now()))
                .thenReturn(Optional.of(policy));
        Member parent = Member.builder().id(40L).fullName("Parent").employer(employer)
                .cardNumber("P-1").active(true).build();

        Member dependent = process(null, parent, Relationship.SON);

        assertThat(parent.getBenefitPolicy()).isSameAs(policy);
        assertThat(parent.getPolicyNumber()).isEqualTo("POL-20");
        assertThat(dependent.getBenefitPolicy()).isSameAs(policy);
        assertThat(dependent.getPolicyNumber()).isEqualTo("POL-20");
    }

    private Member process(BenefitPolicy selectedPolicy, Member parent, Relationship relationship) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Row row = workbook.createSheet().createRow(0);
            row.createCell(0).setCellValue("Test Member");
            return processor.processRowForImport(
                    row, 1, Map.of("fullName", 0), employer, selectedPolicy,
                    parent, relationship, null);
        }
    }
}

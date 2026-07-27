package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.dto.BenefitStructureImportResult;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitDefinitionRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitGroupRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;

@ExtendWith(MockitoExtension.class)
class BenefitStructureImportServiceTest {

    @Mock BenefitPolicyRepository policyRepository;
    @Mock BenefitPolicyRuleRepository ruleRepository;
    @Mock MedicalCategoryRepository categoryRepository;
    @Mock BenefitGroupRepository groupRepository;
    @Mock BenefitLimitBucketRepository bucketRepository;
    @Mock BenefitRuleBucketRepository linkRepository;
    @Mock BenefitDefinitionRepository definitionRepository;

    @InjectMocks BenefitStructureImportService service;

    @Test
    void importWorkbook_allowsInitialImportForActiveSuspendedEmptyPolicy() {
        BenefitPolicy policy = BenefitPolicy.builder()
                .id(1L)
                .status(BenefitPolicy.BenefitPolicyStatus.SUSPENDED)
                .active(true)
                .build();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(ruleRepository.countByBenefitPolicyId(1L)).thenReturn(0L);
        when(groupRepository.countByPolicyId(1L)).thenReturn(0L);
        when(bucketRepository.countByPolicyId(1L)).thenReturn(0L);
        when(linkRepository.countByRuleBenefitPolicyId(1L)).thenReturn(0L);

        BenefitStructureImportResult result = service.importWorkbook(
                1L,
                workbook("benefits.xlsx", service.createSimplifiedTemplate()),
                false,
                BenefitStructureImportService.ImportMode.MERGE);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getMode()).isEqualTo("MERGE");
    }

    @Test
    void importWorkbook_blocksInitialImportForCancelledEmptyPolicy() {
        BenefitPolicy policy = BenefitPolicy.builder()
                .id(1L)
                .status(BenefitPolicy.BenefitPolicyStatus.CANCELLED)
                .active(true)
                .build();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(ruleRepository.countByBenefitPolicyId(1L)).thenReturn(0L);
        when(groupRepository.countByPolicyId(1L)).thenReturn(0L);
        when(bucketRepository.countByPolicyId(1L)).thenReturn(0L);
        when(linkRepository.countByRuleBenefitPolicyId(1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.importWorkbook(
                1L,
                workbook("benefits.xlsx", service.createSimplifiedTemplate()),
                false,
                BenefitStructureImportService.ImportMode.MERGE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("مسودة");
    }

    private MockMultipartFile workbook(String name, byte[] content) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }
}

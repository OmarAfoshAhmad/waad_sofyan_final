package com.waad.tba.modules.semantic.service;

import com.waad.tba.modules.semantic.dto.MedicalClassificationRequestDto;
import com.waad.tba.modules.semantic.dto.MedicalClassificationResultDto;
import com.waad.tba.modules.semantic.entity.enums.BodySystem;
import com.waad.tba.modules.semantic.entity.enums.MedicalSpecialty;
import com.waad.tba.modules.semantic.entity.enums.ProcedureType;
import com.waad.tba.modules.semantic.repository.MedicalSemanticRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class MedicalSemanticClassificationServiceTest {

    @Mock
    private MedicalSemanticRuleRepository ruleRepository;

    @InjectMocks
    private MedicalSemanticClassificationService classificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(ruleRepository.findByIsActiveTrueOrderByPriorityDesc()).thenReturn(Collections.emptyList());
    }

    private MedicalClassificationRequestDto createRequest(String serviceName) {
        MedicalClassificationRequestDto req = new MedicalClassificationRequestDto();
        req.setServiceName(serviceName);
        return req;
    }

    @Test
    void testSebaceousCyst() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("Removal of single sebaceous cyst"));
        assertEquals(BodySystem.SKIN_SOFT_TISSUE.name(), res.getBodySystem());
        assertEquals(ProcedureType.MINOR_SURGERY.name(), res.getProcedureType());
        assertEquals("OUTPATIENT", res.getLikelyEncounterType());
        assertEquals("CAT023", res.getSuggestedInsuranceCategoryCode());
        assertFalse(res.getRequiresReview());
    }

    @Test
    void testMriBrain() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("MRI Brain with contrast"));
        assertEquals(MedicalSpecialty.RADIOLOGY.name(), res.getMedicalSpecialty());
        assertEquals(ProcedureType.IMAGING.name(), res.getProcedureType());
        assertEquals("CAT024", res.getSuggestedInsuranceCategoryCode());
    }

    @Test
    void testToothExtraction() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("Tooth extraction simple"));
        assertEquals(MedicalSpecialty.DENTISTRY.name(), res.getMedicalSpecialty());
        assertEquals(ProcedureType.DENTAL_ROUTINE.name(), res.getProcedureType());
        assertEquals("CAT028", res.getSuggestedInsuranceCategoryCode());
    }

    @Test
    void testDentalImplant() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("Dental implant titanium"));
        assertEquals(MedicalSpecialty.DENTISTRY.name(), res.getMedicalSpecialty());
        assertEquals(ProcedureType.DENTAL_ADVANCED.name(), res.getProcedureType());
        assertEquals("CAT031", res.getSuggestedInsuranceCategoryCode());
    }

    @Test
    void testCataractSurgery() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("Cataract surgery left eye"));
        assertTrue(res.getRequiresReview());
        assertEquals("SUB-INPAT-GENERAL", res.getSuggestedInsuranceCategoryCode());
    }

    @Test
    void testPlasticSurgery() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("Plastic surgery nose"));
        assertTrue(res.getRequiresReview());
    }

    @Test
    void testInfertility() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("IVF consultation"));
        assertTrue(res.getRequiresReview());
    }

    @Test
    void testAnesthesiaOnly() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("General anesthesia"));
        assertTrue(res.getRequiresReview());
    }

    @Test
    void testOrganTransplant() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("Kidney transplant"));
        assertTrue(res.getRequiresReview());
        assertEquals("CAT013", res.getSuggestedInsuranceCategoryCode());
    }

    @Test
    void testMiscellaneous() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("Other miscellaneous service"));
        assertTrue(res.getConfidenceScore() < 0.5);
    }

    @Test
    void testArabicSebaceousCyst() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("استئصال كيس دهني"));
        assertEquals(BodySystem.SKIN_SOFT_TISSUE.name(), res.getBodySystem());
        assertEquals("CAT023", res.getSuggestedInsuranceCategoryCode());
    }

    @Test
    void testArabicMri() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("أشعة رنين مغناطيسي للدماغ"));
        assertEquals("CAT024", res.getSuggestedInsuranceCategoryCode());
    }

    @Test
    void testArabicToothExtraction() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("خلع سن"));
        assertEquals("CAT028", res.getSuggestedInsuranceCategoryCode());
    }

    @Test
    void testArabicDentalImplant() {
        MedicalClassificationResultDto res = classificationService.classifyService(createRequest("زراعة أسنان"));
        assertEquals("CAT031", res.getSuggestedInsuranceCategoryCode());
    }
}

package com.waad.tba.modules.claimcontext.service;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every approved insurance classification must be understood by the resolver.
 *
 * <p>The list below is the approved reference — «التصنيفات التأمينية المعتمدة» —
 * exactly as a provider writes it on a price list. Before V211 the alias table
 * covered eight of the fifteen, so the rest reached the review screen
 * unclassified no matter how correct the file was, and the reason shown blamed
 * the file rather than the missing mapping.
 *
 * <p>Two of the fifteen are not coverage classifications and are asserted
 * separately: maternity is a context, and psychiatry is deferred by decision.
 *
 * <p>The labels are passed in their original spelling on purpose. Normalization
 * is part of what is under test: an alias stored in a spelling the normalizer
 * never produces is an alias that never matches, and that failure is silent.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ApprovedClassificationCoverageIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ClaimContextSourceResolver resolver;

    private static final List<String> APPROVED = List.of(
            "إيواء",
            "عيادات خارجية",
            "تمريض منزلي",
            "علاج طبيعي",
            "إصابات عمل",
            "طب نفسي",
            "ولادة طبيعية وقيصرية",
            "مضاعفات حمل",
            "أشعة تحاليل رسوم أطباء",
            "الرنين المغناطيسي والمقطعية والاشعة التشخيصية",
            "علاجات وأدوية روتينية",
            "أجهزة ومعدات",
            "أسنان روتيني",
            "أسنان تجميلي",
            "النظارة الطبية");

    @ParameterizedTest
    @ValueSource(strings = {
            "إيواء", "عيادات خارجية", "تمريض منزلي", "علاج طبيعي", "إصابات عمل",
            "مضاعفات حمل", "أشعة تحاليل رسوم أطباء",
            "الرنين المغناطيسي والمقطعية والاشعة التشخيصية",
            "علاجات وأدوية روتينية", "أجهزة ومعدات", "أسنان روتيني",
            "أسنان تجميلي", "النظارة الطبية"
    })
    void resolvesEveryApprovedClassificationToACategory(String approvedLabel) {
        var resolution = resolver.resolve(approvedLabel, null);

        assertThat(resolution)
                .as("التصنيف المعتمد «%s» يجب أن يكون له تسمية في جدول التسميات", approvedLabel)
                .isPresent();
        assertThat(resolution.get().medicalCategoryCode())
                .as("التصنيف المعتمد «%s» يجب أن يشير إلى تصنيف طبي", approvedLabel)
                .isNotBlank();
        assertThat(resolution.get().claimContextCode())
                .as("التصنيف المعتمد «%s» يجب أن يحمل سياقاً معرَّفاً", approvedLabel)
                .isNotBlank();
    }

    /**
     * Maternity is a context, not a coverage classification: the label fixes the
     * context and leaves the category to the service itself, since a normal birth
     * and a caesarean are different services under the same heading. An alias
     * with no category may not be auto-approved -- V201 forbids exactly that --
     * so it reaches review with its context already known.
     */
    @Test
    void treatsMaternityAsAContextRatherThanAClassification() {
        var resolution = resolver.resolve("ولادة طبيعية وقيصرية", null);

        assertThat(resolution).isPresent();
        assertThat(resolution.get().claimContextCode()).isEqualTo("MATERNITY");
        assertThat(resolution.get().medicalCategoryCode()).isNull();
        assertThat(resolution.get().requiresReview()).isTrue();
    }

    /**
     * A label that is genuinely unknown must still come back empty. Without this
     * the test above would pass just as well against a resolver that answers
     * everything, which would be worse than answering nothing.
     */
    @Test
    void leavesAnUnknownLabelUnresolved() {
        assertThat(resolver.resolve("تصنيف غير موجود إطلاقا", null)).isEmpty();
    }

    /**
     * Psychiatry is deferred by decision until sessions and drugs are separated.
     * Pinned so that no future edit quietly picks one of the two halves: a
     * provisional alias would be recording a decision nobody has taken.
     */
    @Test
    void leavesPsychiatryDeferred() {
        assertThat(resolver.resolve("طب نفسي", null)).isEmpty();
    }

    /** Guards the list itself against being trimmed to make the suite pass. */
    @Test
    void keepsAllFifteenApprovedClassificationsUnderTest() {
        assertThat(APPROVED).hasSize(15);
    }
}

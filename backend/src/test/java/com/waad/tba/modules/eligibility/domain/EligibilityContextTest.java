package com.waad.tba.modules.eligibility.domain;

import com.waad.tba.modules.member.entity.Member;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for SECTION_02 HIGH finding #8: getDaysSinceEnrollment()
 * only looked at member.startDate, while WaitingPeriodRule's local
 * enrollmentDate falls back to member.joinDate when startDate is null. That
 * mismatch made getDaysSinceEnrollment() return -1 for any member missing
 * startDate — triggering an incorrect SERVICE_DATE_BEFORE_COVERAGE denial
 * regardless of the member's actual enrollment date. This was the first test
 * ever added for the `eligibility` module (it previously had zero coverage).
 */
class EligibilityContextTest {

    @Test
    void fallsBackToJoinDateWhenStartDateIsMissing() {
        Member member = Member.builder()
                .startDate(null)
                .joinDate(LocalDate.of(2026, 1, 1))
                .build();
        EligibilityContext context = EligibilityContext.builder()
                .member(member)
                .serviceDate(LocalDate.of(2026, 3, 1))
                .build();

        long days = context.getDaysSinceEnrollment();

        assertThat(days)
                .as("must match WaitingPeriodRule's own startDate->joinDate fallback, not report -1")
                .isEqualTo(59L);
    }

    @Test
    void prefersStartDateOverJoinDateWhenBothPresent() {
        Member member = Member.builder()
                .startDate(LocalDate.of(2026, 2, 1))
                .joinDate(LocalDate.of(2026, 1, 1))
                .build();
        EligibilityContext context = EligibilityContext.builder()
                .member(member)
                .serviceDate(LocalDate.of(2026, 3, 1))
                .build();

        assertThat(context.getDaysSinceEnrollment()).isEqualTo(28L);
    }

    @Test
    void returnsNegativeOneWhenNeitherDateIsSet() {
        Member member = Member.builder().startDate(null).joinDate(null).build();
        EligibilityContext context = EligibilityContext.builder()
                .member(member)
                .serviceDate(LocalDate.of(2026, 3, 1))
                .build();

        assertThat(context.getDaysSinceEnrollment()).isEqualTo(-1L);
    }
}

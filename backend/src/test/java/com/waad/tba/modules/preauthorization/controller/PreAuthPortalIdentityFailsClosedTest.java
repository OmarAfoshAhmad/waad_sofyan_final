package com.waad.tba.modules.preauthorization.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The provider portal's bulk endpoint persists real pre-authorizations. It
 * used to invent every piece of identity it was not given: the member fell
 * back to id 1, the provider was hardcoded to 1, the medical category was
 * hardcoded to 1, and the expected service date became today.
 *
 * None of those are cosmetic defaults. The category decides which benefit
 * rule applies and therefore which buckets a hold lands on; the provider
 * decides which contract prices it; the date decides which policy and which
 * contract terms are resolved at all. Once the approval service starts
 * placing holds, a request built on invented identity takes limit from a
 * member who never asked for the service.
 *
 * These tests exist because the fix is a behaviour, not a comment: each
 * missing field must stop the request rather than be guessed.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class PreAuthPortalIdentityFailsClosedTest extends PostgresIntegrationTestBase {

    @Autowired private PreAuthPortalController controller;
    @Autowired private JdbcTemplate jdbc;

    /** A payload with every required field present; tests remove one at a time. */
    private Map<String, Object> completePayload() {
        Map<String, Object> line = new HashMap<>();
        line.put("code", "SVC-1");
        line.put("name", "Service");
        line.put("contractPrice", "100.00");
        line.put("medicalCategoryId", 7);

        Map<String, Object> member = new HashMap<>();
        member.put("id", 42);

        Map<String, Object> payload = new HashMap<>();
        payload.put("member", member);
        payload.put("providerId", 9);
        payload.put("expectedServiceDate", "2026-12-01");
        payload.put("lines", List.of(line));
        return payload;
    }

    private long preauthCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pre_authorizations", Long.class);
    }

    @Test
    void aRequestWithoutAMemberIsRefusedRatherThanAssignedToMemberOne() {
        Map<String, Object> payload = completePayload();
        payload.remove("member");
        long before = preauthCount();

        assertThatThrownBy(() -> controller.submitBulkRequest(payload))
                .hasMessageContaining("المستفيد");
        assertThat(preauthCount()).as("nothing may be persisted").isEqualTo(before);
    }

    @Test
    void aRequestWithoutAProviderIsRefusedRatherThanAssignedToProviderOne() {
        Map<String, Object> payload = completePayload();
        payload.remove("providerId");
        long before = preauthCount();

        assertThatThrownBy(() -> controller.submitBulkRequest(payload))
                .hasMessageContaining("مقدم الخدمة");
        assertThat(preauthCount()).isEqualTo(before);
    }

    @Test
    void aLineWithoutAMedicalCategoryIsRefusedRatherThanAssignedToCategoryOne() {
        Map<String, Object> payload = completePayload();
        @SuppressWarnings("unchecked")
        Map<String, Object> line = new HashMap<>((Map<String, Object>) ((List<?>) payload.get("lines")).get(0));
        line.remove("medicalCategoryId");
        payload.put("lines", List.of(line));
        long before = preauthCount();

        // The classification decides the benefit rule and therefore the
        // buckets. There is no safe default -- least of all id 1.
        assertThatThrownBy(() -> controller.submitBulkRequest(payload))
                .hasMessageContaining("تصنيف طبي");
        assertThat(preauthCount()).isEqualTo(before);
    }

    @Test
    void aRequestWithoutAnExpectedServiceDateIsRefusedRatherThanDatedToday() {
        Map<String, Object> payload = completePayload();
        payload.remove("expectedServiceDate");
        long before = preauthCount();

        // Defaulting to today resolves a FUTURE service against today's
        // policy and today's contract terms.
        assertThatThrownBy(() -> controller.submitBulkRequest(payload))
                .hasMessageContaining("تاريخ الخدمة المتوقع");
        assertThat(preauthCount()).isEqualTo(before);
    }

    @Test
    void aRequestWithNoLinesIsRefused() {
        Map<String, Object> payload = completePayload();
        payload.put("lines", List.of());
        long before = preauthCount();

        assertThatThrownBy(() -> controller.submitBulkRequest(payload))
                .hasMessageContaining("بنود");
        assertThat(preauthCount()).isEqualTo(before);
    }

    @Test
    void noPathWritesTheHardcodedIdentityAnyMore() {
        // A structural check on the source itself: the fabricated values were
        // easy to reintroduce and invisible in review.
        String source;
        try {
            source = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/waad/tba/modules/preauthorization/controller/PreAuthPortalController.java"),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }

        assertThat(source).doesNotContain(".serviceCategoryId(1L)");
        assertThat(source).doesNotContain("Long providerId = 1L");
        assertThat(source).doesNotContain(".expectedServiceDate(LocalDate.now())");
    }
}
